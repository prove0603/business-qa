package com.zhuangjie.qa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhuangjie.qa.db.entity.PromptTemplate;
import com.zhuangjie.qa.db.service.PromptTemplateDbService;
import com.zhuangjie.qa.rag.HybridDocumentRetriever;
import com.zhuangjie.qa.rag.SummarizingChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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
            - 使用与用户提问相同的语言回答
            - 【引用规则】当你引用了某份文档的内容时，请在引用处标注来源，格式：[来源：《文档标题》]
            - 如果多个文档都与问题相关，请综合引用并分别标注来源
            - 回答结尾，用一行列出所有引用过的文档标题
            """;

    private static final String DEFAULT_ANALYSIS_SYSTEM_PROMPT =
            "You are a technical analyst. Analyze code changes and suggest document updates.";

    // ─── ChatMemory（带摘要压缩） ───

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
    public ChatMemory chatMemory(ChatModel chatModel,
                                 @Value("${qa.rag.memory-max-messages:16}") int maxMessages,
                                 @Value("${qa.rag.memory-summarize-threshold:24}") int summarizeThreshold) {
        log.info("Creating SummarizingChatMemory: maxMessages={}, summarizeThreshold={}", maxMessages, summarizeThreshold);
        return new SummarizingChatMemory(chatModel, maxMessages, summarizeThreshold);
    }

    // ─── RAG Advisor（混合检索 + 查询改写） ───

    @Bean
    public RetrievalAugmentationAdvisor ragAdvisor(
            ObjectProvider<VectorStore> vectorStoreProvider,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ChatModel chatModel,
            @Value("${qa.rag.top-k:5}") int topK,
            @Value("${qa.rag.similarity-threshold:0.45}") double similarityThreshold,
            @Value("${qa.rag.rrf-k:60}") int rrfK) {

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("VectorStore not available, RAG advisor will use empty retriever");
            return RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(query -> java.util.List.of())  // 空检索器：总是返回空文档列表
                    .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
                    .build();
        }

        DocumentRetriever hybridRetriever = new HybridDocumentRetriever(
                vectorStore, jdbcTemplate, objectMapper, topK, similarityThreshold, rrfK);

        RewriteQueryTransformer queryRewriter = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .targetSearchSystem("enterprise business knowledge base with technical documentation")
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryRewriter)
                .documentRetriever(hybridRetriever)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)            // 即使没找到文档，也让 AI 继续回答
                        .build())
                .build();
    }

    // ─── ChatClient ───

    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory,
                                 RetrievalAugmentationAdvisor ragAdvisor,
                                 LlmRetryAdvisor llmRetryAdvisor,
                                 ObjectProvider<PromptTemplateDbService> promptDbServiceProvider) {
        String systemPrompt = loadPrompt(promptDbServiceProvider, "CHAT_SYSTEM", DEFAULT_CHAT_SYSTEM_PROMPT);
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        llmRetryAdvisor,
                        ragAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    @Bean
    public ChatClient analysisChatClient(ChatModel chatModel,
                                         LlmRetryAdvisor llmRetryAdvisor,
                                         ObjectProvider<PromptTemplateDbService> promptDbServiceProvider) {
        String systemPrompt = loadPrompt(promptDbServiceProvider, "ANALYSIS_SYSTEM", DEFAULT_ANALYSIS_SYSTEM_PROMPT);
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(llmRetryAdvisor)
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
