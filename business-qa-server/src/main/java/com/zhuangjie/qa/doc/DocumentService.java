package com.zhuangjie.qa.doc;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.db.service.DocumentDbService;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.file.ContentTypeDetectionService;
import com.zhuangjie.qa.file.DocumentParseService;
import com.zhuangjie.qa.file.FileStorageService;
import com.zhuangjie.qa.pojo.req.DocumentCreateReq;
import com.zhuangjie.qa.pojo.req.DocumentUpdateReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理服务：支持在线编辑创建和文件上传创建两种方式。
 * 文档创建/更新后自动触发异步向量化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentDbService documentDbService;
    private final ModuleDbService moduleDbService;
    private final VectorSyncService vectorSyncService;
    private final FileStorageService fileStorageService;
    private final DocumentParseService documentParseService;
    private final ContentTypeDetectionService contentTypeDetectionService;

    /**
     * 在线编辑创建（直接传入文本内容）
     */
    public QaDocument create(DocumentCreateReq req) {
        QaDocument doc = new QaDocument();
        doc.setModuleId(req.moduleId());
        doc.setTitle(req.title());
        doc.setContent(req.content());
        doc.setFileType(req.fileType() != null ? req.fileType() : "MARKDOWN");
        doc.setContentHash(DigestUtil.sha256Hex(req.content()));
        doc.setFileSize(0L);
        doc.setChunkCount(0);
        doc.setVectorized(false);
        doc.setVersion(1);
        doc.setStatus(1);
        documentDbService.save(doc);

        try {
            vectorSyncService.asyncVectorize(doc.getId());
        } catch (Exception e) {
            log.warn("文档创建成功但向量化触发失败，可稍后手动向量化: docId={}, error={}", doc.getId(), e.getMessage());
        }
        return doc;
    }

    /**
     * 文件上传创建（支持 PDF/Word/TXT/Markdown 等）
     */
    public QaDocument uploadFile(Long moduleId, String title, MultipartFile file) {
        contentTypeDetectionService.validateFile(file);

        String detectedType = contentTypeDetectionService.detectContentType(file);
        String parsedContent = documentParseService.parseContent(file);

        if (parsedContent.isBlank()) {
            throw new RuntimeException("文件解析后无有效文本内容");
        }

        String fileKey = fileStorageService.uploadDocument(file);

        String fileType = resolveFileType(file.getOriginalFilename(), detectedType);

        QaDocument doc = new QaDocument();
        doc.setModuleId(moduleId);
        doc.setTitle(title != null && !title.isBlank() ? title : extractTitle(file.getOriginalFilename()));
        doc.setContent(parsedContent);
        doc.setFileType(fileType);
        doc.setContentHash(DigestUtil.sha256Hex(parsedContent));
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setContentType(detectedType);
        doc.setFileKey(fileKey);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setVectorized(false);
        doc.setVersion(1);
        doc.setStatus(1);
        documentDbService.save(doc);

        log.info("文档上传成功: [{}] fileKey={}, parsedLength={}", doc.getTitle(), fileKey, parsedContent.length());
        try {
            vectorSyncService.asyncVectorize(doc.getId());
        } catch (Exception e) {
            log.warn("文档上传成功但向量化触发失败，可稍后手动向量化: docId={}, error={}", doc.getId(), e.getMessage());
        }
        return doc;
    }

    public QaDocument update(Long id, DocumentUpdateReq req) {
        QaDocument doc = documentDbService.getById(id);
        if (doc == null) {
            throw new RuntimeException("Document not found: " + id);
        }

        String newHash = DigestUtil.sha256Hex(req.content());
        boolean contentChanged = !newHash.equals(doc.getContentHash());

        doc.setTitle(req.title());
        doc.setContent(req.content());
        doc.setContentHash(newHash);
        if (contentChanged) {
            doc.setVectorized(false);
            doc.setVersion(doc.getVersion() + 1);
        }
        documentDbService.updateById(doc);

        if (contentChanged) {
            try {
                vectorSyncService.asyncVectorize(doc.getId());
            } catch (Exception e) {
                log.warn("文档更新成功但向量化触发失败: docId={}, error={}", doc.getId(), e.getMessage());
            }
        }
        return doc;
    }

    public void delete(Long id) {
        QaDocument doc = documentDbService.getById(id);
        if (doc != null && doc.getFileKey() != null) {
            fileStorageService.deleteFile(doc.getFileKey());
        }
        vectorSyncService.deleteVectors(id);
        documentDbService.lambdaUpdate()
                .eq(QaDocument::getId, id)
                .set(QaDocument::getStatus, 0)
                .update();
    }

    public QaDocument getById(Long id) {
        return documentDbService.getById(id);
    }

    public List<QaDocument> listByModuleId(Long moduleId) {
        return documentDbService.listByModuleId(moduleId);
    }

    public Page<QaDocument> page(Long moduleId, int current, int size) {
        return documentDbService.lambdaQuery()
                .eq(moduleId != null, QaDocument::getModuleId, moduleId)
                .eq(QaDocument::getStatus, 1)
                .orderByDesc(QaDocument::getUpdateTime)
                .page(new Page<>(current, size));
    }

    public byte[] downloadFile(Long id) {
        QaDocument doc = documentDbService.getById(id);
        if (doc == null || doc.getFileKey() == null) {
            throw new RuntimeException("该文档无关联文件");
        }
        return fileStorageService.downloadFile(doc.getFileKey());
    }

    private String resolveFileType(String filename, String contentType) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "MARKDOWN";
            if (lower.endsWith(".pdf")) return "PDF";
            if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "WORD";
            if (lower.endsWith(".txt")) return "TEXT";
        }
        if (contentType != null) {
            if (contentType.contains("pdf")) return "PDF";
            if (contentType.contains("word")) return "WORD";
            if (contentType.contains("markdown")) return "MARKDOWN";
        }
        return "TEXT";
    }

    private String extractTitle(String filename) {
        if (filename == null) return "Untitled";
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
    }
}
