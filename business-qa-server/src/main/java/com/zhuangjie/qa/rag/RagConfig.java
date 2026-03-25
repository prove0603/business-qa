package com.zhuangjie.qa.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;

/**
 * RAG（检索增强生成）基础设施配置。
 *
 * 通过 qa.rag.enabled=true 开关控制是否启用。
 * 配置了两个核心 Bean：
 * 1. EmbeddingModel — DashScope 的 text-embedding-v3，将文本转为 1024 维向量
 * 2. PgVectorStore — 基于 PostgreSQL + pgvector 扩展的向量存储
 *
 * 数据流：文本 → EmbeddingModel → 1024维浮点数组 → PgVectorStore（qa_vector 表）
 *
 * 为什么 EmbeddingModel 不用 Spring AI 自带的 OpenAI Embedding？
 * 因为 DashScope 的 Embedding API 参数格式与 OpenAI 不完全兼容（维度参数等），
 * 所以自定义了 DashScopeEmbeddingModel 适配器，底层走 DashScope SDK。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "qa.rag.enabled", havingValue = "true")
public class RagConfig {

    /**
     * DashScope Embedding 模型 Bean。
     * API Key 优先从 spring.ai.openai.api-key 读取（与 Chat 共用），
     * 回退到环境变量 AI_DASHSCOPE_API_KEY。
     */
    @Bean
    public EmbeddingModel dashScopeEmbeddingModel(
            @Value("${spring.ai.openai.api-key:${AI_DASHSCOPE_API_KEY:}}") String apiKey,
            @Value("${qa.rag.embedding-model:text-embedding-v3}") String embeddingModel) {
        return new DashScopeEmbeddingModel(apiKey, embeddingModel);
    }

    /**
     * PgVectorStore Bean — 基于 PostgreSQL 的向量存储。
     *
     * 核心配置项：
     * - dimensions(1024): 向量维度，必须与 EmbeddingModel 输出一致
     * - COSINE_DISTANCE: 余弦相似度，适合文本语义检索
     * - HNSW 索引: 层次化可导航小世界图，平衡检索速度和精度
     * - initializeSchema(true): 启动时自动创建 qa_vector 表和 HNSW 索引
     * - vectorTableName("qa_vector"): 自定义表名（默认是 vector_store）
     */
    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashScopeEmbeddingModel) {
        log.info("Creating PgVectorStore with HNSW index, cosine distance, dimensions=1024");
        return PgVectorStore.builder(jdbcTemplate, dashScopeEmbeddingModel)
                .dimensions(1024)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("qa_vector")
                .build();
    }
}
