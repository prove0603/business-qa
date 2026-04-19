package com.zhuangjie.qa.chat;

import com.zhuangjie.qa.config.ModelConfigService;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.guardrail.GuardrailService;
import com.zhuangjie.qa.guardrail.GuardrailService.GuardrailCheckResult;
import com.zhuangjie.qa.rag.VectorService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话服务核心类：编排完整的 AI 对话流程。
 * 调用链路：限流 → 输入护栏 → 引用预检索 → ChatClient(Retry + RAG + Memory) → 熔断 → 输出护栏
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ModelConfigService modelConfigService;
    private final ModuleDbService moduleDbService;
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    private final GuardrailService guardrailService;
    private final RateLimiter aiRateLimiter;
    private final CircuitBreaker aiCircuitBreaker;
    private final VectorService vectorService;

    /**
     * 流式对话完整链路：限流 → 输入护栏 → 引用预检索 → RAG+QueryRewrite(Advisor) → LLM(含Retry) → 熔断 → 输出护栏。
     *
     * @return Flux[String] 第一个元素可能是 "[REFS:json]" 格式的引用来源数据
     */
    public Flux<String> streamChat(String question, List<Long> moduleIds, Long sessionId) {
        // ====== 第1层：限流 ======
        try {
            RateLimiter.waitForPermission(aiRateLimiter);
        } catch (RequestNotPermitted e) {
            log.warn("AI rate limited: {}", e.getMessage());
            return Flux.just("⚠ 请求频率过高，请稍后再试。当前限制：每分钟 "
                    + aiRateLimiter.getRateLimiterConfig().getLimitForPeriod() + " 次请求。");
        }

        // ====== 第2层：输入护栏 ======
        GuardrailCheckResult checkResult = guardrailService.checkInput(question);
        if (checkResult.blocked()) {
            log.info("Input blocked by guardrail '{}': {}", checkResult.ruleName(), checkResult.action());
            return Flux.just("[GUARDRAIL]" + checkResult.message());
        }

        List<Long> effectiveModuleIds = buildEffectiveModuleIds(moduleIds);
        String conversationId = sessionId != null ? sessionId.toString() : "default";

        log.debug("Streaming chat: question={}, moduleIds={}", question, effectiveModuleIds);

        // ====== 引用溯源：预检索文档，稍后随响应一起返回 ======
        List<SourceReference> sourceRefs = prefetchSourceRefs(question, effectiveModuleIds);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .user(question)
                .advisors(a -> {
                    a.param(ChatMemory.CONVERSATION_ID, conversationId);
                    if (!effectiveModuleIds.isEmpty()) {
                        var fb = new FilterExpressionBuilder();
                        a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                                fb.in("module_id", effectiveModuleIds.toArray()).build());
                    }
                });

        ChatOptions dynamicOptions = modelConfigService.getActiveChatOptions();
        if (dynamicOptions != null) {
            spec = spec.options(dynamicOptions);
        }

        ChatClient.ChatClientRequestSpec finalSpec = spec;
        try {
            toolCallbackProviders.forEach(provider -> finalSpec.toolCallbacks(provider.getToolCallbacks()));
        } catch (Exception e) {
            log.warn("MCP Tools 挂载失败（Server 可能未启动），本次对话将不使用工具: {}", e.getMessage());
        }

        // 先发送引用来源标记（前端解析后展示在引用面板中）
        Flux<String> refsEvent = sourceRefs.isEmpty()
                ? Flux.empty()
                : Flux.just("[REFS:" + toRefsJson(sourceRefs) + "]");

        // ====== 第3层：流式调用 + 熔断 ======
        Flux<String> contentStream = finalSpec.stream()
                .content()
                .map(guardrailService::filterOutput)
                .transformDeferred(CircuitBreakerOperator.of(aiCircuitBreaker))
                .onErrorResume(CallNotPermittedException.class, e -> {
                    log.warn("AI circuit breaker OPEN: {}", e.getMessage());
                    return Flux.just("⚠ AI 服务暂时不可用（熔断器已开启，连续错误过多），请稍后再试。");
                })
                .onErrorResume(e -> {
                    log.error("Chat stream error: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Client failed to initialize")) {
                        return Flux.just("⚠ SQL 分析服务暂不可用（MCP Server 未启动），已跳过工具调用。请稍后重试，或仅使用文档问答功能。");
                    }
                    return Flux.just("⚠ 回答生成出错：" + e.getMessage());
                });

        return refsEvent.concatWith(contentStream);
    }

    /**
     * 预检索：在 RAG Advisor 链之外做一次轻量级向量搜索，用于填充引用来源面板。
     * 这里用原始 query 检索（未经 QueryRewrite），结果可能与 Advisor 略有不同，但足够作为参考。
     */
    private List<SourceReference> prefetchSourceRefs(String question, List<Long> moduleIds) {
        try {
            List<Document> docs = vectorService.search(question, moduleIds, 5);
            return docs.stream()
                    .map(doc -> new SourceReference(
                            String.valueOf(doc.getMetadata().getOrDefault("doc_title", "未知文档")),
                            String.valueOf(doc.getMetadata().getOrDefault("module_code", "")),
                            doc.getText().length() > 200 ? doc.getText().substring(0, 200) + "…" : doc.getText()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Pre-fetch source refs failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String toRefsJson(List<SourceReference> refs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < refs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"docTitle\":\"").append(escapeJson(refs.get(i).docTitle()))
                    .append("\",\"moduleCode\":\"").append(escapeJson(refs.get(i).moduleCode()))
                    .append("\",\"excerpt\":\"").append(escapeJson(refs.get(i).excerpt()))
                    .append("\"}");
        }
        return sb.append("]").toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    /** 构建有效的模块 ID 列表：将 COMMON 类型模块自动合并到用户选择的模块中 */
    private List<Long> buildEffectiveModuleIds(List<Long> userSelectedIds) {
        List<QaModule> commonModules = moduleDbService.listByType("COMMON");
        List<Long> commonIds = commonModules.stream().map(QaModule::getId).toList();
        List<Long> effective = new ArrayList<>(commonIds);
        if (userSelectedIds != null) {
            for (Long id : userSelectedIds) {
                if (!effective.contains(id)) {
                    effective.add(id);
                }
            }
        }
        return effective;
    }

    public record SourceReference(String docTitle, String moduleCode, String excerpt) {}
}
