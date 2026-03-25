package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.chat.ChatHistoryService;
import com.zhuangjie.qa.chat.ChatService;
import com.zhuangjie.qa.common.Result;
import com.zhuangjie.qa.db.entity.ChatMessage;
import com.zhuangjie.qa.db.entity.ChatSession;
import com.zhuangjie.qa.pojo.req.ChatReq;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能问答 REST 接口。
 *
 * 核心接口 POST /api/chat/stream 使用 SSE（Server-Sent Events）实现流式响应，
 * 前端通过 fetch + ReadableStream 逐步接收 LLM 生成的文本片段。
 *
 * 对话会话管理：创建、列表、查看、删除会话及消息。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    /**
     * SSE 流式问答接口。
     *
     * 流程：
     * 1. 若 sessionId 为空，自动创建新会话（标题取问题的前 50 字符）
     * 2. 先保存用户消息到数据库
     * 3. 调用 ChatService.streamChat() 获取 Flux<String> 流
     * 4. doOnNext：每收到一个文本片段就追加到 fullResponse
     * 5. doOnComplete：流结束后提取引用来源，保存助手消息到数据库
     *
     * 返回 Content-Type: text/event-stream，前端用 ReadableStream 消费。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatReq req) {
        Long sessionId = req.sessionId();
        if (sessionId == null) {
            String title = req.question().length() > 50
                    ? req.question().substring(0, 50) + "..."
                    : req.question();
            ChatSession session = chatHistoryService.createSession(title, req.moduleIds());
            sessionId = session.getId();
        }

        // 先持久化用户消息
        chatHistoryService.saveMessage(sessionId, "user", req.question(), null);

        Long finalSessionId = sessionId;
        StringBuilder fullResponse = new StringBuilder();

        return chatService.streamChat(req.question(), req.moduleIds(), sessionId)
                // 每个流式片段追加到 StringBuilder，用于最终保存完整回答
                .doOnNext(fullResponse::append)
                // 流结束后：提取引用来源 + 保存助手消息
                .doOnComplete(() -> {
                    var refs = chatService.extractSourceRefs(req.question(), req.moduleIds());
                    chatHistoryService.saveMessage(finalSessionId, "assistant", fullResponse.toString(), refs);
                });
    }

    /** 手动创建对话会话 */
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestParam(required = false) String title,
                                              @RequestBody(required = false) List<Long> moduleIds) {
        return Result.ok(chatHistoryService.createSession(
                title != null ? title : "New Conversation", moduleIds));
    }

    /** 获取最近的对话会话列表 */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> listSessions(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(chatHistoryService.listRecentSessions(limit));
    }

    @GetMapping("/sessions/{id}")
    public Result<ChatSession> getSession(@PathVariable Long id) {
        return Result.ok(chatHistoryService.getSession(id));
    }

    /** 获取某个会话的所有消息（用于前端恢复历史对话） */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long id) {
        return Result.ok(chatHistoryService.getSessionMessages(id));
    }

    /** 删除会话及其所有消息 */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatHistoryService.deleteSession(id);
        return Result.ok();
    }
}
