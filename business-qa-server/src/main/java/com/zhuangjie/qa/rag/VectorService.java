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

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final VectorStore vectorStore;
    private final ChunkSplitter chunkSplitter;

    /**
     * 将文档拆分为分块，通过 Embedding 模型转为向量后存入 PgVectorStore。
     */
    public int vectorizeDocument(QaDocument doc, QaModule module) {
        // 先删除旧的向量数据，避免重复
        deleteByDocumentId(doc.getId());

        List<String> chunks = chunkSplitter.split(doc.getContent());
        // 递归分块：按段落/长度等策略将长文本拆成适合 Embedding 的片段
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = Map.of(
                    "document_id", doc.getId(),
                    "module_id", module.getId(),
                    "module_code", module.getModuleCode(),
                    "module_type", module.getModuleType(),
                    "doc_title", doc.getTitle(),
                    "chunk_index", i
            );
            documents.add(new Document(chunks.get(i), metadata));
        }

        if (!documents.isEmpty()) {
            // 分批写入，降低单次 add 的内存与数据库压力
            int batchSize = 10;
            for (int i = 0; i < documents.size(); i += batchSize) {
                List<Document> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
                vectorStore.add(batch);
            }
        }

        log.info("Vectorized document [{}]: {} chunks", doc.getTitle(), documents.size());
        return documents.size();
    }

    /**
     * 向量相似度搜索：按模块过滤后，返回与查询语义最接近的 topK 个文档分块。
     */
    public List<Document> search(String query, List<Long> moduleIds, int topK) {
        var builder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (moduleIds != null && !moduleIds.isEmpty()) {
            var fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.in("module_id", moduleIds.toArray()).build());
        }

        List<Document> result = vectorStore.similaritySearch(builder.build());
        return result != null ? result : List.of();
    }

    public void deleteByDocumentId(Long documentId) {
        vectorStore.delete("document_id == " + documentId);
    }
}
