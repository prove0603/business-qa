package com.zhuangjie.qa.chat;

import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.rag.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 智能问答的核心服务。
 *
 * 实现了 Retrieval-Augmented Generation（检索增强生成）的完整流程：
 * 1. 根据用户问题，从向量库检索相关文档片段
 * 2. 将检索到的文档内容拼接成增强 Prompt（augmentedQuestion）
 * 3. 通过 ChatClient 发送给 LLM，以 SSE 流式返回回答
 *
 * 对话记忆由 AiChatConfig 中配置的 MessageChatMemoryAdvisor 自动管理，
 * 本类只需传入 conversationId 即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** RAG 问答用的 ChatClient，带对话记忆 Advisor */
    private final ChatClient chatClient;
    /** 向量检索服务，负责从 PgVectorStore 中检索相关文档 */
    private final VectorService vectorService;
    private final ModuleDbService moduleDbService;

    /**
     * RAG 流式问答的核心方法。
     *
     * 执行流程：
     * 1. buildEffectiveModuleIds() — 合并用户选择的模块 + 所有 COMMON 类型模块
     * 2. vectorService.search() — 用用户原始问题做向量检索，取 top-5 最相关的文档片段
     * 3. 拼接 augmentedQuestion — 将检索到的文档内容 + 用户问题组合成 RAG Prompt
     * 4. chatClient.prompt().stream() — 发送给 LLM 并返回 SSE 流
     *
     * 注意：augmentedQuestion 作为 user 消息发出，而非 system 消息。
     * System Prompt 已在 AiChatConfig 中通过 defaultSystem() 预设。
     *
     * @param question  用户原始问题
     * @param moduleIds 用户选择的模块 ID 列表（COMMON 模块会自动包含，无需手动选）
     * @param sessionId 对话会话 ID，用于 ChatMemory 隔离不同会话的历史
     * @return Flux<String> SSE 流式响应，每个元素是 LLM 生成的一个文本片段
     */
    public Flux<String> streamChat(String question, List<Long> moduleIds, Long sessionId) {
        // 合并 COMMON 模块（全局知识库）和用户指定的模块
        List<Long> effectiveModuleIds = buildEffectiveModuleIds(moduleIds);

        // 向量检索：用用户问题在指定模块范围内检索最相关的 5 个文档片段
        List<Document> docs = vectorService.search(question, effectiveModuleIds, 5);

        // 将检索到的文档片段格式化为上下文文本
        // 格式：【文档标题】\n文档内容，多个文档用 --- 分隔
        String context = docs.isEmpty()
                ? "（无相关文档）"
                : docs.stream()
                        .map(d -> "【%s】\n%s".formatted(d.getMetadata().getOrDefault("doc_title", "Unknown"), d.getText()))
                        .collect(Collectors.joining("\n\n---\n\n"));

        // 构建 RAG 增强 Prompt：指令 + 参考文档 + 用户原始问题
        // 这个 prompt 作为 user 消息发送，LLM 会结合 system prompt 和对话历史一起理解
        String augmentedQuestion = """
                基于以下参考文档回答用户的问题。
                如果文档中没有相关信息，请明确说明。
                在回答中引用文档标题。
                
                ## 参考文档
                %s
                
                ## 用户问题
                %s
                """.formatted(context, question);

        // conversationId 用于 MessageChatMemoryAdvisor 定位对话历史
        String conversationId = sessionId != null ? sessionId.toString() : "default";

        // 调用 LLM：
        // .prompt() 创建请求构建器
        // .user() 设置用户消息（增强后的 prompt）
        // .advisors() 传入 conversationId 参数给 MemoryAdvisor
        // .stream().content() 返回 Flux<String> 流式文本
        return chatClient.prompt()
                .user(augmentedQuestion)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    /**
     * 构建有效的模块 ID 列表。
     * COMMON 类型的模块是全局知识库，无论用户是否选择都会包含在检索范围内。
     * 然后合并用户手动选择的模块（去重）。
     */
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

    /**
     * 提取本次问答引用的来源文档信息。
     * 在 ChatController 中，回答流结束后调用此方法，将引用来源一并存入数据库。
     *
     * 注意：这里会再次调用 vectorService.search()，与 streamChat 中的检索是重复的。
     * 优化方向：在 streamChat 中缓存检索结果，避免重复检索。
     */
    public List<SourceReference> extractSourceRefs(String question, List<Long> moduleIds) {
        List<Long> effectiveModuleIds = buildEffectiveModuleIds(moduleIds);
        List<Document> docs = vectorService.search(question, effectiveModuleIds, 5);

        return docs.stream()
                .map(d -> new SourceReference(
                        (String) d.getMetadata().getOrDefault("doc_title", "Unknown"),
                        (String) d.getMetadata().getOrDefault("module_code", ""),
                        d.getText().length() > 200 ? d.getText().substring(0, 200) + "..." : d.getText()
                ))
                .distinct()
                .toList();
    }

    /** 引用来源记录，包含文档标题、模块编码和内容摘要 */
    public record SourceReference(String docTitle, String moduleCode, String excerpt) {}
}
