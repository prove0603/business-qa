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
 * 向量检索擅长语义相似匹配，BM25 擅长精确关键词命中。两路结果合并后，
 * 能覆盖"换了说法但意思一样"和"关键词精确匹配"两种场景。
 */
@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int topK;
    private final double similarityThreshold;
    /** RRF 常数 k，控制排名靠后文档的衰减速度；经典值 60 */
    private final int rrfK;

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

    @Override
    public List<Document> retrieve(Query query) {
        Object filterObj = query.context().get(VectorStoreDocumentRetriever.FILTER_EXPRESSION);
        Filter.Expression filterExpression = filterObj instanceof Filter.Expression fe ? fe : null;

        // ── 第一路：向量语义检索 ──
        List<Document> vectorDocs = vectorSearch(query.text(), filterExpression);

        // ── 第二路：PG 全文关键词检索 ──
        List<Document> bm25Docs = fullTextSearch(query.text());

        // ── RRF 合并 ──
        List<Document> merged = rrfMerge(vectorDocs, bm25Docs);

        log.debug("Hybrid retrieval: vector={}, bm25={}, merged={} (query='{}')",
                vectorDocs.size(), bm25Docs.size(), merged.size(),
                query.text().length() > 60 ? query.text().substring(0, 60) + "…" : query.text());

        return merged;
    }

    private List<Document> vectorSearch(String queryText, Filter.Expression filterExpression) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK * 2)
                    .similarityThreshold(similarityThreshold);
            if (filterExpression != null) {
                builder.filterExpression(filterExpression);
            }
            List<Document> result = vectorStore.similaritySearch(builder.build());
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("Vector search failed, fallback to BM25 only: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * PG 全文检索：使用 'simple' 配置对 qa_vector 表的 content 列做 FTS。
     * 'simple' 配置按空白/标点分词，对中英文混合文本有基本支持。
     * 如需更好的中文分词效果，可安装 zhparser 扩展。
     */
    private List<Document> fullTextSearch(String queryText) {
        try {
            String sql = """
                    SELECT id, content, metadata::text AS metadata_json
                    FROM qa_vector
                    WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', ?)
                    ORDER BY ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) DESC
                    LIMIT ?
                    """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata_json");
                Map<String, Object> metadata = parseMetadata(metadataJson);
                Document doc = new Document(content, metadata);
                return doc;
            }, queryText, queryText, topK * 2);
        } catch (Exception e) {
            log.warn("Full-text search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reciprocal Rank Fusion：对两路结果按排名倒数加权合并。
     * RRF_score(d) = Σ 1 / (k + rank_i(d))
     * 出现在两路结果中的文档得分更高，自然排到前面。
     */
    private List<Document> rrfMerge(List<Document> vectorDocs, List<Document> bm25Docs) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Document> docMap = new LinkedHashMap<>();

        for (int i = 0; i < vectorDocs.size(); i++) {
            String key = docKey(vectorDocs.get(i));
            scores.merge(key, 1.0 / (rrfK + i + 1), Double::sum);
            docMap.putIfAbsent(key, vectorDocs.get(i));
        }

        for (int i = 0; i < bm25Docs.size(); i++) {
            String key = docKey(bm25Docs.get(i));
            scores.merge(key, 1.0 / (rrfK + i + 1), Double::sum);
            docMap.putIfAbsent(key, bm25Docs.get(i));
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .collect(Collectors.toList());
    }

    private String docKey(Document doc) {
        if (doc.getId() != null) {
            return doc.getId();
        }
        return String.valueOf(doc.getText().hashCode());
    }

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
