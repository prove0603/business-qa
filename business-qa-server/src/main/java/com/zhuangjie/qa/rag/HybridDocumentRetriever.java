package com.zhuangjie.qa.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器：向量语义检索 + PG 全文检索（BM25），用 RRF（Reciprocal Rank Fusion）合并排序。
 * <p>
 * 向量检索擅长语义相似匹配（"文件上传报错" ≈ "文件上传异常处理"），
 * BM25 擅长精确关键词命中（"Tika" 必须完全匹配才能找到）。
 * 两路结果用 RRF 合并后，既能处理同义表达，又不会漏掉精确关键词。
 * <p>
 * 实现了 Spring AI 的 {@link DocumentRetriever} 接口，可以直接嵌入 {@code RetrievalAugmentationAdvisor}。
 */
@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    /** 最终返回给 LLM 的文档数量 */
    private final int topK;
    /** 向量相似度阈值，低于该值的文档会被过滤掉（0~1，越高越严格） */
    private final double similarityThreshold;
    /** RRF 常数 k，控制排名靠后文档的衰减速度；经典值 60 */
    private final int rrfK;
    /** PG 全文检索使用的配置名：zhparser 可用时用 'chinese'，否则用 'simple' */
    private volatile String ftsConfig;

    public HybridDocumentRetriever(VectorStore vectorStore, JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper,
                                   int topK, double similarityThreshold, int rrfK) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.rrfK = rrfK;
    }

    /**
     * 检测 PG 是否安装了 zhparser 扩展并创建了 'chinese' 全文检索配置。
     * 首次调用时执行一次 SQL 探测，结果缓存在 ftsConfig 字段中。
     */
    private String getFtsConfig() {
        if (ftsConfig != null) {
            return ftsConfig;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_ts_config WHERE cfgname = 'chinese'", Integer.class);
            if (count != null && count > 0) {
                ftsConfig = "chinese";
                log.info("zhparser detected: using 'chinese' FTS config for BM25 search");
            } else {
                ftsConfig = "simple";
                log.info("zhparser not found: using 'simple' FTS config (install zhparser for better Chinese tokenization)");
            }
        } catch (Exception e) {
            ftsConfig = "simple";
            log.warn("Failed to detect FTS config, using 'simple': {}", e.getMessage());
        }
        return ftsConfig;
    }

    /**
     * RAG Advisor 在处理每个用户提问时调用此方法。
     * Query 对象包含查询文本和上下文（如模块过滤条件）。
     */
    @Override
    public List<Document> retrieve(Query query) {
        // 从 query 上下文中取出 ChatService 传入的模块过滤条件
        // （用户在前端勾选了哪些模块，就只检索这些模块下的文档）
        Object filterObj = query.context().get(VectorStoreDocumentRetriever.FILTER_EXPRESSION);
        Filter.Expression filterExpression = filterObj instanceof Filter.Expression fe ? fe : null;

        // ── 第一路：向量语义检索 ──
        // 把用户查询文本转成 Embedding 向量，在 pgvector 中找余弦距离最近的文档
        // 优点：能识别同义词、换种说法也能匹配
        // 缺点：可能漏掉包含精确关键词但语义不够"近"的文档
        List<Document> vectorDocs = vectorSearch(query.text(), filterExpression);

        // ── 第二路：PG 全文关键词检索（BM25） ──
        // 用 PostgreSQL 内置的全文检索功能，按关键词精确匹配
        // 优点：关键词精确命中，不需要语义理解
        // 缺点：同义词换种说法就搜不到
        List<Document> bm25Docs = fullTextSearch(query.text());

        // ── RRF 合并两路结果 ──
        // 把两路各自的排名转化为分数，加权合并，取 top-K
        List<Document> merged = rrfMerge(vectorDocs, bm25Docs);

        log.debug("Hybrid retrieval: vector={}, bm25={}, merged={} (query='{}')",
                vectorDocs.size(), bm25Docs.size(), merged.size(),
                query.text().length() > 60 ? query.text().substring(0, 60) + "…" : query.text());

        return merged;
    }

    /**
     * 向量检索：利用 Embedding 模型把查询文本转为高维向量，
     * 然后在 pgvector 中用 HNSW 索引找余弦距离最近的 topK*2 条文档。
     * <p>
     * 多取 2 倍是因为后面要和 BM25 结果合并后再筛选，多取一些候选效果更好。
     */
    private List<Document> vectorSearch(String queryText, Filter.Expression filterExpression) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK * 2)                       // 多取 2 倍候选，合并后再截断
                    .similarityThreshold(similarityThreshold); // 过滤掉相似度太低的噪音文档
            if (filterExpression != null) {
                builder.filterExpression(filterExpression); // 模块过滤：只搜用户选中的模块
            }
            List<Document> result = vectorStore.similaritySearch(builder.build());
            return result != null ? result : List.of();
        } catch (Exception e) {
            // 向量检索失败时不中断，退化为仅 BM25 检索
            log.warn("Vector search failed, fallback to BM25 only: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * PG 全文检索（BM25）：直接用 SQL 查询 qa_vector 表。
     * <p>
     * to_tsvector('simple', content)：把文档内容转换为"词向量"（这里是分词后的词条列表）
     * plainto_tsquery('simple', ?)：把用户查询转换为"查询表达式"
     * @@ 运算符：检查词向量是否匹配查询表达式
     * ts_rank(...)：计算匹配相关性分数，用于排序
     * <p>
     * 'simple' 配置：按空白和标点分词，不做词干提取。
     * 对中英文混合文本有基本支持；如需更精确的中文分词，可安装 zhparser 扩展。
     */
    private List<Document> fullTextSearch(String queryText) {
        try {
            String cfg = getFtsConfig();
            String sql = """
                    SELECT id, content, metadata::text AS metadata_json
                    FROM qa_vector
                    WHERE to_tsvector('%s', content) @@ plainto_tsquery('%s', ?)
                    ORDER BY ts_rank(to_tsvector('%s', content), plainto_tsquery('%s', ?)) DESC
                    LIMIT ?
                    """.formatted(cfg, cfg, cfg, cfg);

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata_json");
                Map<String, Object> metadata = parseMetadata(metadataJson);
                return new Document(content, metadata);
            }, queryText, queryText, topK * 2);
        } catch (Exception e) {
            // 全文检索失败时不中断，退化为仅向量检索
            log.warn("Full-text search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reciprocal Rank Fusion（RRF）：把两路检索结果按"排名倒数"加权合并。
     * <p>
     * 公式：RRF_score(d) = Σ 1 / (k + rank_i(d))
     *   - k 是常数（默认 60），防止排名第 1 的文档权重过大
     *   - rank_i(d) 是文档 d 在第 i 路检索中的排名（从 1 开始）
     *   - 如果文档同时出现在两路结果中，两个分数相加，所以排名更靠前
     * <p>
     * 举例（k=60）：
     *   - 文档 A：向量排名第 1 → 1/(60+1)=0.0164，BM25 排名第 3 → 1/(60+3)=0.0159，总分=0.0323
     *   - 文档 B：只在向量排名第 2 → 1/(60+2)=0.0161，BM25 没命中 → 总分=0.0161
     *   → A 排在 B 前面，因为 A 同时被两路检索命中
     */
    private List<Document> rrfMerge(List<Document> vectorDocs, List<Document> bm25Docs) {
        // key=文档唯一标识，value=RRF 累计分数
        Map<String, Double> scores = new LinkedHashMap<>();
        // key=文档唯一标识，value=文档对象（用于最终返回）
        Map<String, Document> docMap = new LinkedHashMap<>();

        // 遍历向量检索结果，按排名计算 RRF 分数
        // i=0 表示排名第 1，分数 = 1/(60+0+1) = 1/61
        // i=1 表示排名第 2，分数 = 1/(60+1+1) = 1/62
        // ...以此类推
        for (int i = 0; i < vectorDocs.size(); i++) {
            String key = docKey(vectorDocs.get(i));
            // merge + Double::sum：如果 key 已存在（即 BM25 也命中了），分数累加
            scores.merge(key, 1.0 / (rrfK + i + 1), Double::sum);
            docMap.putIfAbsent(key, vectorDocs.get(i));
        }

        // 遍历 BM25 检索结果，同样按排名计算分数
        // 如果某文档已经被向量检索命中过，这里会累加分数（merge + Double::sum）
        for (int i = 0; i < bm25Docs.size(); i++) {
            String key = docKey(bm25Docs.get(i));
            scores.merge(key, 1.0 / (rrfK + i + 1), Double::sum);
            docMap.putIfAbsent(key, bm25Docs.get(i));
        }

        // 按 RRF 总分降序排序，取前 topK 个
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * 生成文档的唯一标识：优先用 Spring AI 自动分配的 UUID，
     * 如果没有（BM25 查出来的文档可能没设 id），则用内容 hashCode 作为 fallback。
     */
    private String docKey(Document doc) {
        if (doc.getId() != null) {
            return doc.getId();
        }
        return String.valueOf(doc.getText().hashCode());
    }

    /** 把 qa_vector 表中 metadata 列（JSONB 类型）的文本形式解析为 Map */
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}", e.getMessage());
            return Map.of();
        }
    }
}
