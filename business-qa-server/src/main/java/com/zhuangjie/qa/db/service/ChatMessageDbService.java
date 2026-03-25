package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChatMessage;
import com.zhuangjie.qa.db.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 对话消息数据库服务层 */
@Service
public class ChatMessageDbService extends ServiceImpl<ChatMessageMapper, ChatMessage> {

    /** 按时间正序获取某会话的所有消息（用于前端展示对话历史） */
    public List<ChatMessage> listBySessionId(Long sessionId) {
        return lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime)
                .list();
    }

    public long countBySessionId(Long sessionId) {
        return lambdaQuery().eq(ChatMessage::getSessionId, sessionId).count();
    }
}
