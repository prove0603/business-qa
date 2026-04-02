package com.zhuangjie.qa.chat;

import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.guardrail.GuardrailService;
import com.zhuangjie.qa.guardrail.GuardrailService.GuardrailCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ModuleDbService moduleDbService;
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    private final GuardrailService guardrailService;

    /**
     * 流式对话：输入护栏 → RAG（Advisor）→ LLM → 输出护栏。
     * MCP Tools 每次请求时挂载（Server 不可用则自动跳过）。
     */
    public Flux<String> streamChat(String question, List<Long> moduleIds, Long sessionId) {
        GuardrailCheckResult checkResult = guardrailService.checkInput(question);
        if (checkResult.blocked()) {
            log.info("Input blocked by guardrail '{}': {}", checkResult.ruleName(), checkResult.action());
            return Flux.just("[GUARDRAIL]" + checkResult.message());
        }

        List<Long> effectiveModuleIds = buildEffectiveModuleIds(moduleIds);
        String conversationId = sessionId != null ? sessionId.toString() : "default";

        log.debug("Streaming chat: question={}, moduleIds={}", question, effectiveModuleIds);

        var spec = chatClient.prompt()
                .user(question)
                .advisors(a -> {
                    a.param(ChatMemory.CONVERSATION_ID, conversationId);
                    if (!effectiveModuleIds.isEmpty()) {
                        var fb = new FilterExpressionBuilder();
                        a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                                fb.in("module_id", effectiveModuleIds.toArray()).build());
                    }
                });

        try {
            toolCallbackProviders.forEach(provider -> spec.toolCallbacks(provider.getToolCallbacks()));
        } catch (Exception e) {
            log.warn("MCP Tools 挂载失败（Server 可能未启动），本次对话将不使用工具: {}", e.getMessage());
        }

        return spec.stream()
                .content()
                .map(guardrailService::filterOutput)
                .onErrorResume(e -> {
                    log.error("Chat stream error: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Client failed to initialize")) {
                        return Flux.just("⚠ SQL 分析服务暂不可用（MCP Server 未启动），已跳过工具调用。请稍后重试，或仅使用文档问答功能。");
                    }
                    return Flux.just("⚠ 回答生成出错：" + e.getMessage());
                });
    }

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
