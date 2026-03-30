package com.zhuangjie.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiChatConfig {

    private static final String SYSTEM_PROMPT = """
            你是一个智能业务助手，具备以下能力：
            1. 基于业务文档的知识问答
            2. 查询SQL性能分析结果（当用户问到SQL风险、SQL问题、SQL性能等话题时，使用可用的工具查询真实数据）
            
            回答规则：
            - 如果文档中没有相关信息，请明确说明
            - 在回答中引用文档标题作为来源
            - 使用与用户提问相同的语言回答
            """;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    /**
     * 主 ChatClient — 同时具备 RAG 文档问答 + MCP 工具调用能力。
     * MCP 工具由 spring-ai-starter-mcp-client 自动注册为 ToolCallbackProvider Bean，
     * 这里通过 ObjectProvider 全部注入，LLM 在需要时自动决定是否调用。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory,
                                  ObjectProvider<ToolCallbackProvider> toolCallbackProviders) {
        var builder = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                );

        toolCallbackProviders.forEach(builder::defaultToolCallbacks);

        return builder.build();
    }

    @Bean
    public ChatClient analysisChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a technical analyst. Analyze code changes and suggest document updates.")
                .build();
    }
}
