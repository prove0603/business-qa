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

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSyncService {

    private final VectorService vectorService;
    private final DocumentDbService documentDbService;
    private final ModuleDbService moduleDbService;

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

            documentDbService.lambdaUpdate()
                    .eq(QaDocument::getId, documentId)
                    .set(QaDocument::getVectorized, chunkCount > 0)
                    .set(QaDocument::getChunkCount, chunkCount)
                    .update();

            log.info("Document vectorized: [{}] {} chunks", doc.getTitle(), chunkCount);
        } catch (Exception e) {
            log.error("Failed to vectorize document {}: {}", documentId, e.getMessage(), e);
            documentDbService.lambdaUpdate()
                    .eq(QaDocument::getId, documentId)
                    .set(QaDocument::getVectorized, false)
                    .set(QaDocument::getChunkCount, 0)
                    .update();
        }
    }

    public void deleteVectors(Long documentId) {
        try {
            vectorService.deleteByDocumentId(documentId);
        } catch (Exception e) {
            log.warn("Failed to delete vectors for document {}: {}", documentId, e.getMessage());
        }
    }

    public void reVectorizeAll() {
        var docs = documentDbService.listUnvectorized();
        log.info("Re-vectorizing {} documents", docs.size());
        for (QaDocument doc : docs) {
            asyncVectorize(doc.getId());
        }
    }
}
