package com.zhuangjie.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** MinIO 对象存储配置属性，从 application.yml 的 qa.storage.* 读取 */
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
