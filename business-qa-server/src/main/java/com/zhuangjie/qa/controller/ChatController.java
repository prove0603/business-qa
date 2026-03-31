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

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatReq req) {
        Long sessionId = req.sessionId();
        boolean isNew = sessionId == null;
        if (isNew) {
            String title = req.question().length() > 50
                    ? req.question().substring(0, 50) + "..."
                    : req.question();
            ChatSession session = chatHistoryService.createSession(title, req.moduleIds());
            sessionId = session.getId();
        }

        chatHistoryService.saveMessage(sessionId, "user", req.question(), null);

        Long finalSessionId = sessionId;
        StringBuilder fullResponse = new StringBuilder();

        Flux<String> sessionEvent = isNew
                ? Flux.just("[SESSION:" + finalSessionId + "]")
                : Flux.empty();

        return sessionEvent.concatWith(
                chatService.streamChat(req.question(), req.moduleIds(), finalSessionId)
                        .doOnNext(fullResponse::append)
                        .doOnComplete(() ->
                                chatHistoryService.saveMessage(finalSessionId, "assistant",
                                        fullResponse.toString(), null))
        );
    }

    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestParam(required = false) String title,
                                              @RequestBody(required = false) List<Long> moduleIds) {
        return Result.ok(chatHistoryService.createSession(
                title != null ? title : "New Conversation", moduleIds));
    }

    @GetMapping("/sessions")
    public Result<List<ChatSession>> listSessions(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(chatHistoryService.listRecentSessions(limit));
    }

    @GetMapping("/sessions/{id}")
    public Result<ChatSession> getSession(@PathVariable Long id) {
        return Result.ok(chatHistoryService.getSession(id));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long id) {
        return Result.ok(chatHistoryService.getSessionMessages(id));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatHistoryService.deleteSession(id);
        return Result.ok();
    }
}
