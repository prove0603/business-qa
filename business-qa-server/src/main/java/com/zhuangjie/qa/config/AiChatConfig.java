package com.zhuangjie.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话相关的 Spring 配置类。
 *
 * 构建了两个 ChatClient Bean：
 * 1. chatClient —— 用于 RAG 智能问答，配置了 System Prompt 和对话记忆 Advisor
 * 2. analysisChatClient —— 用于 Git 变更分析，无记忆，每次独立调用
 *
 * 核心概念：
 * - ChatModel: Spring AI 自动配置的 LLM 底层连接（通过 spring.ai.openai.* 配置指向 DashScope）
 * - ChatClient: 高级对话客户端，支持 System Prompt、Advisor 链、流式输出
 * - Advisor: 请求/响应拦截器，在发送给 LLM 前后自动执行增强逻辑
 * - ChatMemory: 对话记忆存储，Advisor 会自动读写历史消息
 */
@Configuration
public class AiChatConfig {

    /**
     * 问答助手的 System Prompt。
     * System Prompt 在每次对话中都会作为第一条 system 消息发给 LLM，
     * 定义了 AI 的角色、行为准则和回答风格。
     */
    private static final String SYSTEM_PROMPT = """
            你是一个业务系统智能问答助手。
            根据提供的业务文档回答用户问题。
            如果文档中没有相关信息，请明确告知用户。
            回答时引用参考文档的标题作为来源。
            使用与用户提问相同的语言进行回答。
            """;

    /**
     * 对话记忆存储 Bean。
     * MessageWindowChatMemory 是滑动窗口实现：只保留最近 N 条消息。
     * 注意：这是内存实现，服务重启后记忆丢失。
     * 项目中同时有 ChatHistoryService 做了 DB 持久化，但两者并未同步。
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    /**
     * RAG 智能问答用的 ChatClient。
     *
     * 配置了 MessageChatMemoryAdvisor，它的工作机制：
     * - 请求前：根据 conversationId 从 ChatMemory 取出历史消息，追加到 messages 列表
     * - 请求后：自动将本次的 user + assistant 消息存回 ChatMemory
     *
     * 使用时通过 .advisors(a -> a.param(CONVERSATION_ID, id)) 传入会话标识。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * Git 变更分析用的 ChatClient。
     * 不配置 Advisor（无记忆），因为代码分析是一次性任务，不需要上下文。
     * 在 SuggestionGenerator 中通过 @Qualifier("analysisChatClient") 注入使用。
     */
    @Bean
    public ChatClient analysisChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个技术分析师。分析代码变更并提出文档更新建议。")
                .build();
    }
}
