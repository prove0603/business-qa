package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChatMessage;
import com.zhuangjie.qa.db.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageDbService extends ServiceImpl<ChatMessageMapper, ChatMessage> {

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
