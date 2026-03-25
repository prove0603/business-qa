package com.zhuangjie.qa.rag;

import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.doc.ChunkSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向量化与检索服务，连接文档管理和 RAG 问答的核心桥梁。
 *
 * 职责：
 * 1. vectorizeDocument() — 将业务文档分块后存入向量库（写入侧）
 * 2. search() — 根据用户问题进行语义检索（读取侧）
 * 3. deleteByDocumentId() — 删除文档对应的所有向量
 *
 * 底层依赖：
 * - VectorStore: Spring AI 的向量库抽象，本项目使用 PgVectorStore（PostgreSQL + pgvector）
 * - ChunkSplitter: 文本分块器，将长文档切成适合 embedding 的片段
 *
 * 向量化时会为每个 chunk 附加 metadata（document_id, module_id, doc_title 等），
 * 检索时可通过 FilterExpression 按 metadata 过滤（如只在指定模块范围内检索）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final VectorStore vectorStore;
    private final ChunkSplitter chunkSplitter;

    /**
     * 将文档向量化并存入向量库。
     *
     * 流程：
     * 1. 先删除该文档的旧向量（全量更新策略）
     * 2. 用 ChunkSplitter 将文档内容分成多个 chunk
     * 3. 为每个 chunk 构造 Spring AI 的 Document 对象（text + metadata）
     * 4. 调用 vectorStore.add() 批量写入，底层会自动调用 EmbeddingModel 生成向量
     *
     * metadata 中的字段作用：
     * - document_id: 关联原始文档，用于删除时定位
     * - module_id: 用于检索时按模块过滤
     * - module_code/module_type: 辅助信息，可在前端展示
     * - doc_title: 在 RAG 拼接 prompt 时展示文档来源
     * - chunk_index: 标识 chunk 在原文中的顺序
     *
     * @return chunk 数量（也是存入的向量条数）
     */
    public int vectorizeDocument(QaDocument doc, QaModule module) {
        // 先删旧向量，避免更新文档后出现新旧 chunk 混合
        deleteByDocumentId(doc.getId());

        List<String> chunks = chunkSplitter.split(doc.getContent());
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            // 每个 chunk 都携带丰富的 metadata，存入 PgVector 的 JSONB 列
            Map<String, Object> metadata = Map.of(
                    "document_id", doc.getId(),
                    "module_id", module.getId(),
                    "module_code", module.getModuleCode(),
                    "module_type", module.getModuleType(),
                    "doc_title", doc.getTitle(),
                    "chunk_index", i
            );
            // Document 是 Spring AI 的核心数据结构，包含文本内容和元数据
            documents.add(new Document(chunks.get(i), metadata));
        }

        if (!documents.isEmpty()) {
            // vectorStore.add() 内部会：
            // 1. 调用 EmbeddingModel.call() 将每个 chunk 文本转为 1024 维向量
            // 2. 将向量 + metadata + 原文存入 PostgreSQL 的 qa_vector 表
            vectorStore.add(documents);
        }

        log.info("Vectorized document [{}]: {} chunks", doc.getTitle(), documents.size());
        return documents.size();
    }

    /**
     * 向量相似性检索。
     *
     * 根据用户问题（query）在向量库中找到语义最相似的文档片段。
     * 底层流程：query 文本 → EmbeddingModel 转为向量 → PgVector HNSW 索引做近似最近邻搜索。
     *
     * @param query     用户问题文本（会被自动转为向量进行检索）
     * @param moduleIds 模块过滤列表，只在这些模块的文档中检索（通过 metadata 中的 module_id 过滤）
     * @param topK      返回最相似的 K 个结果
     * @return 检索到的 Document 列表，按相似度降序排列
     */
    public List<Document> search(String query, List<Long> moduleIds, int topK) {
        var builder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        // 如果指定了模块范围，构建 FilterExpression 过滤条件
        // 生成的 SQL 类似：WHERE metadata->>'module_id' IN (1, 2, 3)
        if (moduleIds != null && !moduleIds.isEmpty()) {
            var fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.in("module_id", moduleIds.toArray()).build());
        }

        List<Document> result = vectorStore.similaritySearch(builder.build());
        return result != null ? result : List.of();
    }

    /**
     * 删除指定文档的所有向量。
     * 在文档更新或删除时调用，确保向量库和文档库数据一致。
     *
     * 已知问题：这里的字符串过滤语法在 Spring AI 1.0.0 中可能不兼容，
     * 应改用 FilterExpressionBuilder 构建过滤表达式。
     */
    public void deleteByDocumentId(Long documentId) {
        vectorStore.delete("document_id == " + documentId);
    }
}
