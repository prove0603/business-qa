package com.zhuangjie.qa.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带摘要压缩的对话记忆：当消息数超过阈值时，用 LLM 将旧消息压缩为一段摘要，
 * 后续对话只携带"摘要 + 最近 N 条消息"，既保留完整上下文又控制 token 消耗。
 * <p>
 * 工作流程：
 * 1. add() 时把消息追加到内存列表
 * 2. 如果消息数 > summarizeThreshold，取最旧的一半消息做摘要
 * 3. get() 时在返回列表头部注入一条 SystemMessage 携带摘要
 */
@Slf4j
public class SummarizingChatMemory implements ChatMemory {

    private static final String SUMMARIZE_PROMPT = """
            请将以下对话内容整合为一段简洁的摘要（不超过 300 字）。
            保留关键的问题、结论和重要细节，去掉寒暄和重复内容。
            
            %s
            
            已有摘要（如有）：
            %s
            
            请直接输出新的完整摘要：""";

    private final ConcurrentHashMap<String, List<Message>> conversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> summaries = new ConcurrentHashMap<>();
    private final ChatModel chatModel;
    private final int maxMessages;
    private final int summarizeThreshold;

    /**
     * @param chatModel           用于生成摘要的模型
     * @param maxMessages         get() 最多返回的近期消息数
     * @param summarizeThreshold  触发摘要的消息数阈值（建议 maxMessages * 1.5 ~ 2）
     */
    public SummarizingChatMemory(ChatModel chatModel, int maxMessages, int summarizeThreshold) {
        this.chatModel = chatModel;
        this.maxMessages = maxMessages;
        this.summarizeThreshold = summarizeThreshold;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
        maybeSummarize(conversationId);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = conversations.getOrDefault(conversationId, List.of());
        int start = Math.max(0, all.size() - maxMessages);
        List<Message> recent = new ArrayList<>(all.subList(start, all.size()));

        String summary = summaries.get(conversationId);
        if (summary != null && !summary.isBlank()) {
            recent.addFirst(new SystemMessage("[历史对话摘要] " + summary));
        }

        return recent;
    }

    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
        summaries.remove(conversationId);
    }

    private void maybeSummarize(String conversationId) {
        List<Message> all = conversations.get(conversationId);
        if (all == null || all.size() <= summarizeThreshold) {
            return;
        }

        int cutoff = all.size() - maxMessages;
        List<Message> toSummarize = new ArrayList<>(all.subList(0, cutoff));
        List<Message> toKeep = new ArrayList<>(all.subList(cutoff, all.size()));

        try {
            String existingSummary = summaries.getOrDefault(conversationId, "无");
            String dialogText = formatMessages(toSummarize);

            String prompt = String.format(SUMMARIZE_PROMPT, dialogText, existingSummary);
            String newSummary = chatModel.call(new Prompt(prompt))
                    .getResult().getOutput().getText();

            summaries.put(conversationId, newSummary);
            conversations.put(conversationId, toKeep);

            log.info("Summarized {} messages for conversation '{}', kept {} recent messages",
                    toSummarize.size(), conversationId, toKeep.size());
        } catch (Exception e) {
            log.warn("Failed to summarize conversation '{}': {}. Falling back to simple truncation.",
                    conversationId, e.getMessage());
            conversations.put(conversationId, toKeep);
        }
    }

    private String formatMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            String role = switch (msg) {
                case UserMessage u -> "用户";
                case AssistantMessage a -> "助手";
                case SystemMessage s -> "系统";
                default -> "未知";
            };
            sb.append(role).append("：").append(msg.getText()).append("\n");
        }
        return sb.toString();
    }
}
