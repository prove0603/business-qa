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

/** MinIO（S3 兼容）客户端配置：创建 S3Client 并确保 Bucket 存在 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final StorageConfig storageConfig;

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
