package com.zhuangjie.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 存储配置属性，绑定 application.yml 中的 qa.storage.* 前缀。
 *
 * 示例配置：
 *   qa.storage.endpoint: http://localhost:9000
 *   qa.storage.access-key: minioadmin
 *   qa.storage.secret-key: minioadmin
 *   qa.storage.bucket: business-qa
 *   qa.storage.region: us-east-1 (默认值，MinIO 不真正使用 region)
 */
@Data
@Component
@ConfigurationProperties(prefix = "qa.storage")
public class StorageConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region = "us-east-1";
}
