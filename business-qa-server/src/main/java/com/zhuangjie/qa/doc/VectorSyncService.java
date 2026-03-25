package com.zhuangjie.qa.doc;

import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.DocumentDbService;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.rag.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 向量同步服务，编排文档的异步向量化流程。
 *
 * 在文档创建/更新/删除时由 DocumentService 调用，
 * 通过 @Async 在独立线程中执行，不阻塞主流程。
 *
 * 注意事项：
 * - @Async 依赖 QaApplication 上的 @EnableAsync 启用
 * - 向量化失败只打日志不抛异常，不会影响文档的 CRUD 操作
 * - 已知并发问题：快速连续编辑同一文档时可能出现竞态条件
 *   （向量化 A 还在执行，向量化 B 已经开始，可能覆盖）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSyncService {

    private final VectorService vectorService;
    private final DocumentDbService documentDbService;
    private final ModuleDbService moduleDbService;

    /**
     * 异步向量化单个文档。
     *
     * 流程：
     * 1. 校验文档状态（存在、未被删除、有内容）
     * 2. 查找文档所属模块（用于构造 metadata）
     * 3. 调用 VectorService.vectorizeDocument() 执行分块 + embedding + 存储
     * 4. 更新文档的 vectorized 状态和 chunkCount
     *
     * 整个方法在 @Async 线程池中执行，调用方不会等待完成。
     */
    @Async
    public void asyncVectorize(Long documentId) {
        try {
            QaDocument doc = documentDbService.getById(documentId);
            if (doc == null || doc.getStatus() == 0) {
                return;
            }

            if (doc.getContent() == null || doc.getContent().isBlank()) {
                log.warn("Document {} has no content, skipping vectorization", documentId);
                return;
            }

            QaModule module = moduleDbService.getById(doc.getModuleId());
            if (module == null) {
                log.warn("Module not found for document: {}", documentId);
                return;
            }

            int chunkCount = vectorService.vectorizeDocument(doc, module);

            // 更新文档的向量化状态：chunkCount > 0 则标记为已向量化
            documentDbService.lambdaUpdate()
                    .eq(QaDocument::getId, documentId)
                    .set(QaDocument::getVectorized, chunkCount > 0)
                    .set(QaDocument::getChunkCount, chunkCount)
                    .update();

            log.info("Document vectorized: [{}] {} chunks", doc.getTitle(), chunkCount);
        } catch (Exception e) {
            // 向量化失败不影响业务，但应考虑加入重试机制
            log.error("Failed to vectorize document {}: {}", documentId, e.getMessage(), e);
        }
    }

    /** 删除指定文档的所有向量，在文档删除时调用 */
    public void deleteVectors(Long documentId) {
        try {
            vectorService.deleteByDocumentId(documentId);
        } catch (Exception e) {
            log.warn("Failed to delete vectors for document {}: {}", documentId, e.getMessage());
        }
    }

    /** 批量重新向量化所有未向量化的文档 */
    public void reVectorizeAll() {
        var docs = documentDbService.listUnvectorized();
        log.info("Re-vectorizing {} documents", docs.size());
        for (QaDocument doc : docs) {
            asyncVectorize(doc.getId());
        }
    }
}
