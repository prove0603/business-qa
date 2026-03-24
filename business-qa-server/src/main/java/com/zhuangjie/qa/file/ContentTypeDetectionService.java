package com.zhuangjie.qa.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 文件类型检测与校验服务
 */
@Slf4j
@Service
public class ContentTypeDetectionService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".txt", ".md", ".markdown", ".rtf"
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    private final Tika tika;

    public ContentTypeDetectionService() {
        this.tika = new Tika();
    }

    public String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("无法检测文件类型，回退到 Content-Type 头部: {}", e.getMessage());
            return file.getContentType();
        }
    }

    public void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过 50MB 限制");
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            boolean extensionAllowed = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
            if (extensionAllowed) {
                return;
            }
        }

        String contentType = detectContentType(file);
        if (!isSupportedMimeType(contentType)) {
            throw new IllegalArgumentException("不支持的文件类型，支持: PDF, Word, TXT, Markdown");
        }
    }

    private boolean isSupportedMimeType(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.contains("pdf")
                || lower.contains("msword")
                || lower.contains("wordprocessingml")
                || lower.startsWith("text/")
                || lower.contains("rtf");
    }

    public boolean isMarkdown(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown");
        }
        return false;
    }
}
