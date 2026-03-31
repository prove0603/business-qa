package com.zhuangjie.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiChatConfig {

    private static final String SYSTEM_PROMPT = """
            你是一个智能业务助手，具备以下能力：
            1. 基于业务文档的知识问答（RAG 自动检索相关文档）
            2. 查询SQL性能分析结果（当用户问到SQL风险、SQL问题、SQL性能等话题时，使用可用的工具查询真实数据）
            
            回答规则：
            - 优先参考 RAG 检索到的文档内容作答
            - 如果文档中没有相关信息，且有可用的工具，请使用工具查询
            - 在回答中引用文档标题作为来源
            - 使用与用户提问相同的语言回答
            """;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public RetrievalAugmentationAdvisor ragAdvisor(ObjectProvider<VectorStore> vectorStoreProvider) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(query -> java.util.List.of())
                    .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
                    .build();
        }
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(0.5)
                        .topK(5)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
    }

    /**
     * 主 ChatClient — RAG + Memory 通过 Advisor 自动处理。
     * MCP Tools 由 ChatService 在每次请求时挂载（容错：Server 不可用时自动跳过）。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory,
                                  RetrievalAugmentationAdvisor ragAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        ragAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    @Bean
    public ChatClient analysisChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a technical analyst. Analyze code changes and suggest document updates.")
                .build();
    }
}
