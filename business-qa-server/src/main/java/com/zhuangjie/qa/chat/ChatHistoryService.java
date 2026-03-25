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

/**
 * 对话历史持久化服务，管理对话会话和消息的数据库存储。
 *
 * 注意：这个服务将对话历史存入 PostgreSQL（t_chat_session / t_chat_message），
 * 但与 Spring AI 的 ChatMemory（内存实现）是独立的两套系统。
 * ChatMemory 用于 LLM 调用时的上下文注入，本服务用于前端展示历史对话。
 * 服务重启后 ChatMemory 丢失，但 DB 中的历史仍在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatSessionDbService chatSessionDbService;
    private final ChatMessageDbService chatMessageDbService;
    private final ObjectMapper objectMapper;

    /** 创建对话会话，记录会话标题和关联的模块过滤条件 */
    public ChatSession createSession(String title, List<Long> moduleIds) {
        ChatSession session = new ChatSession();
        session.setTitle(title);
        session.setModuleFilter(toJson(moduleIds));
        session.setMessageCount(0);
        chatSessionDbService.save(session);
        return session;
    }

    /**
     * 保存对话消息（user 或 assistant）。
     * assistant 消息会附带引用来源（SourceReference 列表），序列化为 JSON 存储。
     * 同时更新会话的消息计数。
     */
    public void saveMessage(Long sessionId, String role, String content, List<ChatService.SourceReference> refs) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        if (refs != null && !refs.isEmpty()) {
            message.setSourceRefs(toJson(refs));
        }
        chatMessageDbService.save(message);

        // 消息计数原子递增
        chatSessionDbService.lambdaUpdate()
                .eq(ChatSession::getId, sessionId)
                .setSql("message_count = message_count + 1")
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

    /** 删除会话时同时删除所有消息 */
    public void deleteSession(Long sessionId) {
        chatMessageDbService.lambdaUpdate().eq(ChatMessage::getSessionId, sessionId).remove();
        chatSessionDbService.removeById(sessionId);
    }

    /** 用 Jackson 将对象序列化为 JSON 字符串 */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return "[]";
        }
    }
}
