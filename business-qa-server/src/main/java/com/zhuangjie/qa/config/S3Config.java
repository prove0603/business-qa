package com.zhuangjie.qa.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

/**
 * MinIO（S3 兼容）客户端配置。
 *
 * 使用 AWS SDK v2 的 S3Client 对接 MinIO。
 * forcePathStyle(true) 是关键配置：MinIO 使用路径风格（endpoint/bucket/key），
 * 而非 AWS 默认的虚拟主机风格（bucket.endpoint/key）。
 *
 * 应用启动时自动检查并创建 bucket（ensureBucketExists）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final StorageConfig storageConfig;

    /**
     * S3Client Bean，配置 MinIO 连接信息。
     * endpoint、accessKey 等从 StorageConfig（qa.storage.* 配置项）读取。
     */
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storageConfig.getAccessKey(),
                storageConfig.getSecretKey()
        );

        return S3Client.builder()
                .endpointOverride(URI.create(storageConfig.getEndpoint()))
                .region(Region.of(storageConfig.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .build();
    }

    /**
     * 应用启动时自动确保 bucket 存在。
     * 先 headBucket 检查是否存在，不存在则 createBucket。
     * 连接 MinIO 失败只打 warn 日志不阻止启动（允许本地开发时不启动 MinIO）。
     */
    @Bean
    public CommandLineRunner ensureBucketExists(S3Client s3Client) {
        return args -> {
            String bucket = storageConfig.getBucket();
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                log.info("MinIO bucket already exists: {}", bucket);
            } catch (NoSuchBucketException e) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            } catch (Exception e) {
                log.warn("Failed to check/create MinIO bucket '{}': {}", bucket, e.getMessage());
            }
        };
    }
}
