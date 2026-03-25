package com.zhuangjie.qa.rag;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * DashScope Embedding 适配器，将阿里云 DashScope SDK 的 TextEmbedding API
 * 适配为 Spring AI 的 EmbeddingModel 接口。
 *
 * 为什么需要这个适配器？
 * Spring AI 内置的 OpenAI EmbeddingModel 虽然也能通过兼容接口调用 DashScope，
 * 但在维度参数、批处理方式等方面不完全兼容，所以直接用 DashScope SDK 实现更可靠。
 *
 * 工作原理：
 * - PgVectorStore.add() 时会调用 call(EmbeddingRequest) 批量生成向量
 * - VectorStore.similaritySearch() 时会调用 embed(String) 将查询文本转为向量
 *
 * 配置：
 * - 模型: text-embedding-v3（通过 qa.rag.embedding-model 配置）
 * - 维度: 1024（固定，与 PgVectorStore 的 dimensions 必须一致）
 * - 批大小: 最多 10 条文本一次请求（DashScope 的批量限制）
 */
@Slf4j
public class DashScopeEmbeddingModel implements EmbeddingModel {

    /** 向量维度，text-embedding-v3 输出 1024 维 */
    private static final int DIMENSIONS = 1024;
    /** 单次 API 调用最大文本数量（DashScope 限制） */
    private static final int MAX_BATCH_SIZE = 10;

    /** DashScope SDK 的 TextEmbedding 客户端 */
    private final TextEmbedding textEmbedding;
    private final String model;
    private final String apiKey;

    public DashScopeEmbeddingModel(String apiKey, String model) {
        this.textEmbedding = new TextEmbedding();
        this.model = model;
        this.apiKey = apiKey;
        log.info("DashScope EmbeddingModel initialized: model={}, dimensions={}", model, DIMENSIONS);
    }

    /**
     * 批量 embedding 接口 —— PgVectorStore.add() 时调用。
     * 将多个文本分批（每批最多 10 条）发送给 DashScope API，合并结果返回。
     *
     * @param request 包含待 embedding 的文本列表
     * @return 每个文本对应的 Embedding（包含 float[] 向量和索引位置）
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>();

        // 分批调用，避免超过 DashScope 单次请求的文本数量限制
        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH_SIZE, texts.size()));
            List<Embedding> batchResult = callDashScope(batch, i);
            embeddings.addAll(batchResult);
        }

        return new EmbeddingResponse(embeddings);
    }

    /** 单个 Document 的 embedding —— 提取文本后委托给 embed(String) */
    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    /**
     * 单文本 embedding —— similaritySearch() 时调用，将查询文本转为向量。
     * 返回的 float[] 会被 PgVectorStore 用于与库中向量计算余弦距离。
     */
    @Override
    public float[] embed(String text) {
        List<Embedding> result = callDashScope(List.of(text), 0);
        if (result.isEmpty()) {
            // 调用失败时返回零向量，不会匹配到任何文档
            return new float[DIMENSIONS];
        }
        return result.getFirst().getOutput();
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * 调用 DashScope TextEmbedding API 的核心方法。
     *
     * @param texts       待 embedding 的文本列表
     * @param indexOffset  当前批次在总列表中的起始索引（用于 Embedding 的 index 字段）
     * @return Embedding 列表，失败时返回空列表（静默失败，上层需注意）
     */
    private List<Embedding> callDashScope(List<String> texts, int indexOffset) {
        try {
            TextEmbeddingParam.TextEmbeddingParamBuilder<?, ?> builder = TextEmbeddingParam.builder()
                    .model(model)
                    .texts(texts);

            if (apiKey != null && !apiKey.isBlank()) {
                builder.apiKey(apiKey);
            }

            TextEmbeddingResult result = textEmbedding.call(builder.build());

            List<Embedding> embeddings = new ArrayList<>();
            if (result.getOutput() != null && result.getOutput().getEmbeddings() != null) {
                for (TextEmbeddingResultItem item : result.getOutput().getEmbeddings()) {
                    // DashScope 返回 List<Double>，需要转为 float[] 适配 Spring AI
                    float[] vector = toFloatArray(item.getEmbedding());
                    embeddings.add(new Embedding(vector, indexOffset + item.getTextIndex()));
                }
            }

            log.debug("DashScope embedding: {} texts, tokens={}",
                    texts.size(),
                    result.getUsage() != null ? result.getUsage().getTotalTokens() : "unknown");

            return embeddings;
        } catch (Exception e) {
            log.error("DashScope embedding call failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** DashScope 返回 Double 列表，Spring AI 需要 float 数组 */
    private float[] toFloatArray(List<Double> doubles) {
        float[] floats = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            floats[i] = doubles.get(i).floatValue();
        }
        return floats;
    }
}
