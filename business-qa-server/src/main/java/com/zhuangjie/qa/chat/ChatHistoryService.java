package com.zhuangjie.qa.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhuangjie.qa.db.entity.ChatMessage;
import com.zhuangjie.qa.db.entity.ChatSession;
import com.zhuangjie.qa.db.service.ChatMessageDbService;
import com.zhuangjie.qa.db.service.ChatSessionDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatSessionDbService chatSessionDbService;
    private final ChatMessageDbService chatMessageDbService;
    private final ObjectMapper objectMapper;

    public ChatSession createSession(String title, List<Long> moduleIds) {
        ChatSession session = new ChatSession();
        session.setTitle(title);
        session.setModuleFilter(toJson(moduleIds));
        session.setMessageCount(0);
        chatSessionDbService.save(session);
        return session;
    }

    public void saveMessage(Long sessionId, String role, String content) {
        saveMessageWithRefs(sessionId, role, content, null);
    }

    public void saveMessageWithRefs(Long sessionId, String role, String content, String refsJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        if (refsJson != null && !refsJson.isBlank()) {
            message.setSourceRefs(refsJson);
        }
        chatMessageDbService.save(message);

        chatSessionDbService.lambdaUpdate()
                .eq(ChatSession::getId, sessionId)
                .setSql("message_count = message_count + 1")
                .set(ChatSession::getUpdateTime, java.time.LocalDateTime.now())
                .update();
    }

    public List<ChatSession> listRecentSessions(int limit) {
        return chatSessionDbService.listRecent(limit);
    }

    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return chatMessageDbService.listBySessionId(sessionId);
    }

    public ChatSession getSession(Long sessionId) {
        return chatSessionDbService.getById(sessionId);
    }

    public void deleteSession(Long sessionId) {
        chatMessageDbService.lambdaUpdate().eq(ChatMessage::getSessionId, sessionId).remove();
        chatSessionDbService.removeById(sessionId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return "[]";
        }
    }
}
