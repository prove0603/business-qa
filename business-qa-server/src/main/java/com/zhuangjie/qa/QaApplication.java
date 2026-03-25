package com.zhuangjie.qa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 业务问答助手应用启动类。
 *
 * @EnableAsync 开启异步方法支持，VectorSyncService、SuggestionGenerator 中的
 * @Async 方法依赖此注解。默认使用 SimpleAsyncTaskExecutor（每次新建线程），
 * 生产环境应配置自定义线程池。
 */
@EnableAsync
@SpringBootApplication
public class QaApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaApplication.class, args);
    }
}
