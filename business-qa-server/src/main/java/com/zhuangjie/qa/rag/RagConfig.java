package com.zhuangjie.qa.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;

/**
 * RAG 向量存储相关的基础设施配置。
 * 仅在配置项 {@code qa.rag.enabled=true} 时生效。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "qa.rag.enabled", havingValue = "true")
public class RagConfig {

    /**
     * 创建 {@link PgVectorStore} 实例，配置 HNSW 索引与余弦距离（COSINE_DISTANCE），用于向量检索。
     */
    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        log.info("Creating PgVectorStore with HNSW index, cosine distance, dimensions=1024");
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("qa_vector")
                .build();
    }
}
