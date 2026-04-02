package com.zhuangjie.qa.config;

import com.zhuangjie.qa.db.entity.PromptTemplate;
import com.zhuangjie.qa.db.service.PromptTemplateDbService;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Configuration
public class AiChatConfig {

    private static final String DEFAULT_CHAT_SYSTEM_PROMPT = """
            你是一个智能业务助手，具备以下能力：
            1. 基于业务文档的知识问答（RAG 自动检索相关文档）
            2. 查询SQL性能分析结果（当用户问到SQL风险、SQL问题、SQL性能等话题时，使用可用的工具查询真实数据）
            
            回答规则：
            - 优先参考 RAG 检索到的文档内容作答
            - 如果文档中没有相关信息，且有可用的工具，请使用工具查询
            - 在回答中引用文档标题作为来源
            - 使用与用户提问相同的语言回答
            """;

    private static final String DEFAULT_ANALYSIS_SYSTEM_PROMPT =
            "You are a technical analyst. Analyze code changes and suggest document updates.";

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
     * 主 ChatClient — 系统提示词从 DB 加载（fallback 到硬编码默认值）。
     * RAG + Memory 通过 Advisor 自动处理，MCP Tools 由 ChatService 在每次请求时挂载。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory,
                                  RetrievalAugmentationAdvisor ragAdvisor,
                                  ObjectProvider<PromptTemplateDbService> promptDbServiceProvider) {
        String systemPrompt = loadPrompt(promptDbServiceProvider, "CHAT_SYSTEM", DEFAULT_CHAT_SYSTEM_PROMPT);
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        ragAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    @Bean
    public ChatClient analysisChatClient(ChatModel chatModel,
                                          ObjectProvider<PromptTemplateDbService> promptDbServiceProvider) {
        String systemPrompt = loadPrompt(promptDbServiceProvider, "ANALYSIS_SYSTEM", DEFAULT_ANALYSIS_SYSTEM_PROMPT);
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }

    private String loadPrompt(ObjectProvider<PromptTemplateDbService> provider, String key, String defaultPrompt) {
        try {
            PromptTemplateDbService service = provider.getIfAvailable();
            if (service != null) {
                PromptTemplate template = service.getByKey(key);
                if (template != null) {
                    log.info("Loaded prompt '{}' from database", key);
                    return template.getContent();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load prompt '{}' from DB, using default: {}", key, e.getMessage());
        }
        log.info("Using default prompt for '{}'", key);
        return defaultPrompt;
    }
}
