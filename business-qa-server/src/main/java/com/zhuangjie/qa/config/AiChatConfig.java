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

    /**
     * RAG（检索增强生成）核心组件配置。
     * 
     * 工作流程：
     * 1. 用户提问 → 2. 检索相关文档 → 3. 将文档注入问题上下文 → 4. AI 基于上下文回答
     * 
     * 参数说明：
     * - similarityThreshold(0.5): 相似度阈值，只返回相似度 >= 0.5 的文档片段
     *   ├─ 范围：0.0 ~ 1.0
     *   ├─ 值越高：匹配越严格，返回的文档越相关但可能遗漏
     *   └─ 值越低：匹配越宽松，返回更多文档但可能包含噪声
     * 
     * - topK(5): 返回最相关的前 5 个文档片段
     *   ├─ 避免返回太多内容导致上下文过长
     *   └─ 同时提供足够的知识片段供 AI 参考
     * 
     * - allowEmptyContext(true): 容错机制
     *   ├─ 当没有检索到相关文档时，仍允许 AI 使用自身知识回答
     *   └─ 防止因知识库缺失导致完全无法回答
     * 
     * ObjectProvider 的作用：
     * - 延迟获取 VectorStore Bean，避免启动时因 Bean 不存在而报错
     * - 实现可选依赖：有 VectorStore 就用 RAG，没有就直接回答
     * - 支持运行时动态切换 VectorStore 实现
     */
    @Bean
    public RetrievalAugmentationAdvisor ragAdvisor(ObjectProvider<VectorStore> vectorStoreProvider) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            // 没有向量数据库时，返回一个"空"的 Advisor（不进行任何检索）
            return RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(query -> java.util.List.of())  // 空检索器：总是返回空文档列表
                    .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
                    .build();
        }
        // 有向量数据库时，配置正常的 RAG 检索流程
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)           // 使用配置的 VectorStore（如 PgVector）
                        .similarityThreshold(0.5)           // 相似度阈值：0.5（中等严格度）
                        .topK(5)                            // 返回最相关的 5 个文档片段
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)            // 即使没找到文档，也让 AI 继续回答
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
