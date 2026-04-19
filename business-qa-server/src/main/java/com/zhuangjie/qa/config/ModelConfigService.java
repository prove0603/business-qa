package com.zhuangjie.qa.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行时模型配置管理：支持查看当前模型、测试模型连通性、动态切换 chat model。
 * 模型名称保存在内存中，重启后恢复为 application.yml 中的默认值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ChatModel chatModel;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String defaultChatModel;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModel;

    private final AtomicReference<String> activeChatModel = new AtomicReference<>();

    public String getActiveChatModel() {
        String override = activeChatModel.get();
        return override != null ? override : defaultChatModel;
    }

    public String getDefaultChatModel() {
        return defaultChatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * 测试指定模型是否可用：发送一个简单 prompt，返回测试结果。
     */
    public TestResult testModel(String modelName) {
        long start = System.currentTimeMillis();
        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(modelName)
                    .temperature(0.1)
                    .maxTokens(20)
                    .build();
            ChatResponse response = chatModel.call(new Prompt("Say ok", options));
            long elapsed = System.currentTimeMillis() - start;
            String content = response.getResult().getOutput().getText();
            log.info("Model test success: model={}, elapsed={}ms, response={}", modelName, elapsed, content);
            return new TestResult(true, content, elapsed, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Model test failed: model={}, elapsed={}ms, error={}", modelName, elapsed, e.getMessage());
            return new TestResult(false, null, elapsed, e.getMessage());
        }
    }

    /**
     * 切换当前使用的 chat model。传 null 或空字符串恢复为默认值。
     */
    public void switchChatModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            activeChatModel.set(null);
            log.info("Chat model reset to default: {}", defaultChatModel);
        } else {
            activeChatModel.set(modelName);
            log.info("Chat model switched to: {}", modelName);
        }
    }

    /**
     * 获取当前生效的 ChatOptions（供 ChatService 使用）。
     * 如果未手动切换过模型，返回 null（使用默认配置）。
     */
    public OpenAiChatOptions getActiveChatOptions() {
        String override = activeChatModel.get();
        if (override == null) {
            return null;
        }
        return OpenAiChatOptions.builder().model(override).build();
    }

    @Data
    public static class TestResult {
        private final boolean success;
        private final String response;
        private final long elapsedMs;
        private final String error;
    }
}
