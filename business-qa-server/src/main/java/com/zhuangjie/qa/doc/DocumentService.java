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
 * 文档管理服务，编排文档的完整生命周期。
 *
 * 支持两种创建方式：
 * 1. 在线编辑创建 —— 用户直接输入 Markdown 内容
 * 2. 文件上传创建 —— 上传 PDF/Word/TXT 等文件，自动解析提取文本
 *
 * 文档创建/更新后会自动触发异步向量化（VectorSyncService），
 * 使文档内容可被 RAG 问答检索到。
 *
 * 数据流：
 * 创建/上传 → 校验 → 解析文本 → 存 MinIO（文件上传时） → 存 PostgreSQL → 异步向量化
 * 更新 → 通过 contentHash 判断内容是否变更 → 变更则重新向量化
 * 删除 → 删 MinIO 文件 → 删向量 → 逻辑删除（status=0）
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
     * 在线编辑创建文档。
     * 用户在前端 Markdown 编辑器中直接编写内容，无文件上传。
     * 创建后立即触发异步向量化。
     */
    public QaDocument create(DocumentCreateReq req) {
        QaDocument doc = new QaDocument();
        doc.setModuleId(req.moduleId());
        doc.setTitle(req.title());
        doc.setContent(req.content());
        doc.setFileType(req.fileType() != null ? req.fileType() : "MARKDOWN");
        // 内容哈希用于后续更新时判断内容是否真的发生了变化
        doc.setContentHash(DigestUtil.sha256Hex(req.content()));
        doc.setFileSize(0L);
        doc.setChunkCount(0);
        doc.setVectorized(false);
        doc.setVersion(1);
        doc.setStatus(1);
        documentDbService.save(doc);

        // 向量化失败不影响文档创建，只打 warn 日志
        try {
            vectorSyncService.asyncVectorize(doc.getId());
        } catch (Exception e) {
            log.warn("文档创建成功但向量化触发失败，可稍后手动向量化: docId={}, error={}", doc.getId(), e.getMessage());
        }
        return doc;
    }

    /**
     * 文件上传创建文档。
     *
     * 完整流程：
     * 1. ContentTypeDetectionService.validateFile() — 校验文件类型和大小
     * 2. ContentTypeDetectionService.detectContentType() — 用 Tika 检测真实 MIME 类型
     * 3. DocumentParseService.parseContent() — 用 Tika 提取文本内容
     * 4. FileStorageService.uploadDocument() — 原件存入 MinIO
     * 5. 保存文档元数据 + 解析后的文本到 PostgreSQL
     * 6. VectorSyncService.asyncVectorize() — 异步触发向量化
     */
    public QaDocument uploadFile(Long moduleId, String title, MultipartFile file) {
        // 校验文件类型（扩展名 + MIME 类型双重检查）和大小（不超过 50MB）
        contentTypeDetectionService.validateFile(file);

        // Tika 检测真实文件类型（不信任客户端传的 Content-Type）
        String detectedType = contentTypeDetectionService.detectContentType(file);
        // Tika 提取文本内容 + TextCleaningService 清洗噪声
        String parsedContent = documentParseService.parseContent(file);

        if (parsedContent.isBlank()) {
            throw new RuntimeException("文件解析后无有效文本内容");
        }

        // 原件存入 MinIO，返回存储路径（如 documents/2024/01/15/abc123.pdf）
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

    /**
     * 更新文档内容。
     * 通过 SHA-256 哈希判断内容是否真正变更，仅在变更时触发重新向量化。
     * 每次内容变更 version 自增。
     */
    public QaDocument update(Long id, DocumentUpdateReq req) {
        QaDocument doc = documentDbService.getById(id);
        if (doc == null) {
            throw new RuntimeException("Document not found: " + id);
        }

        // 用 SHA-256 对比新旧内容哈希，避免无意义的重新向量化
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

        // 仅内容变更时重新向量化
        if (contentChanged) {
            try {
                vectorSyncService.asyncVectorize(doc.getId());
            } catch (Exception e) {
                log.warn("文档更新成功但向量化触发失败: docId={}, error={}", doc.getId(), e.getMessage());
            }
        }
        return doc;
    }

    /**
     * 逻辑删除文档：删除 MinIO 文件 + 删除向量 + 标记 status=0。
     * 不做物理删除，保留数据可恢复。
     */
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

    /** 根据文件扩展名和 MIME 类型推断文档类型标识 */
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

    /** 从文件名中提取标题（去掉扩展名） */
    private String extractTitle(String filename) {
        if (filename == null) return "Untitled";
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
    }
}
