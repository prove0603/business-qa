package com.zhuangjie.qa.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 文件类型检测与校验服务。
 *
 * 使用 Apache Tika 检测文件的真实 MIME 类型（基于文件内容，而非扩展名或客户端声明），
 * 防止恶意文件伪装上传。
 *
 * 校验逻辑：先检查扩展名白名单，再用 Tika 检测 MIME 类型。
 */
@Slf4j
@Service
public class ContentTypeDetectionService {

    /** 允许上传的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".txt", ".md", ".markdown", ".rtf"
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    /** Tika 实例，用于检测文件真实 MIME 类型 */
    private final Tika tika;

    public ContentTypeDetectionService() {
        this.tika = new Tika();
    }

    /**
     * 检测文件的真实 MIME 类型。
     * Tika 会读取文件头部的 magic bytes 来判断，比扩展名更可靠。
     */
    public String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("无法检测文件类型，回退到 Content-Type 头部: {}", e.getMessage());
            return file.getContentType();
        }
    }

    /**
     * 校验上传文件的合法性。
     * 双重校验：扩展名白名单 + MIME 类型检测。
     * 扩展名在白名单内则直接通过，否则进一步用 Tika 检测 MIME 类型。
     */
    public void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过 50MB 限制");
        }

        // 先检查扩展名白名单（快速路径）
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            boolean extensionAllowed = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
            if (extensionAllowed) {
                return;
            }
        }

        // 扩展名不在白名单，用 Tika 检测 MIME 类型做兜底校验
        String contentType = detectContentType(file);
        if (!isSupportedMimeType(contentType)) {
            throw new IllegalArgumentException("不支持的文件类型，支持: PDF, Word, TXT, Markdown");
        }
    }

    /** 判断 MIME 类型是否在支持范围内 */
    private boolean isSupportedMimeType(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.contains("pdf")
                || lower.contains("msword")
                || lower.contains("wordprocessingml")
                || lower.startsWith("text/")
                || lower.contains("rtf");
    }

    /** 判断文件是否为 Markdown 格式 */
    public boolean isMarkdown(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown");
        }
        return false;
    }
}
