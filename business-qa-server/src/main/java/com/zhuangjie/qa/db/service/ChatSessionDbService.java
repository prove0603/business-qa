package com.zhuangjie.qa.db.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhuangjie.qa.db.entity.ChatSession;
import com.zhuangjie.qa.db.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 对话会话数据库服务层 */
@Service
public class ChatSessionDbService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    /** 按更新时间倒序获取最近的会话列表 */
    public List<ChatSession> listRecent(int limit) {
        return lambdaQuery()
                .orderByDesc(ChatSession::getUpdateTime)
                .last("LIMIT " + limit)
                .list();
    }
}
