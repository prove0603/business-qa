package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChatSession;
import com.zhuangjie.qa.db.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionDbService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    public List<ChatSession> listRecent(int limit) {
        return lambdaQuery()
                .orderByDesc(ChatSession::getUpdateTime)
                .last("LIMIT " + limit)
                .list();
    }
}
