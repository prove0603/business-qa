package com.zhuangjie.qa.file;

import com.zhuangjie.qa.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储服务，基于 S3 协议对接 MinIO。
 *
 * 存储路径格式：{prefix}/{yyyy/MM/dd}/{uuid12}.{ext}
 * 例如：documents/2024/01/15/a1b2c3d4e5f6.pdf
 *
 * 使用 AWS SDK v2 的 S3Client，通过 S3Config 配置的 endpoint 指向 MinIO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;
    private final StorageConfig storageConfig;

    /** 上传文档文件到 MinIO 的 documents/ 目录下 */
    public String uploadDocument(MultipartFile file) {
        return uploadFile(file, "documents");
    }

    /** 从 MinIO 下载文件为字节数组 */
    public byte[] downloadFile(String fileKey) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .build();
            return s3Client.getObjectAsBytes(getRequest).asByteArray();
        } catch (S3Exception e) {
            log.error("下载文件失败: {} - {}", fileKey, e.getMessage(), e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }

    /** 从 MinIO 删除文件，删除失败只打日志不抛异常 */
    public void deleteFile(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return;
        }
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("文件删除成功: {}", fileKey);
        } catch (S3Exception e) {
            log.error("删除文件失败: {} - {}", fileKey, e.getMessage(), e);
        }
    }

    /** 拼接文件的公开访问 URL */
    public String getFileUrl(String fileKey) {
        return String.format("%s/%s/%s", storageConfig.getEndpoint(), storageConfig.getBucket(), fileKey);
    }

    /** 上传文件到 MinIO，返回存储的 fileKey */
    private String uploadFile(MultipartFile file, String prefix) {
        String originalFilename = file.getOriginalFilename();
        String fileKey = generateFileKey(originalFilename, prefix);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("文件上传成功: {} -> {}", originalFilename, fileKey);
            return fileKey;
        } catch (IOException e) {
            log.error("读取上传文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件读取失败");
        } catch (S3Exception e) {
            log.error("上传文件到 MinIO 失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件存储失败: " + e.getMessage());
        }
    }

    /** 生成存储路径：prefix/yyyy/MM/dd/uuid12.ext */
    private String generateFileKey(String originalFilename, String prefix) {
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String ext = extractExtension(originalFilename);
        return String.format("%s/%s/%s%s", prefix, datePath, uuid, ext);
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx >= 0 ? filename.substring(dotIdx) : "";
    }
}
