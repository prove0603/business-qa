package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息实体，对应 t_chat_message 表。
 *
 * role 取值：user（用户问题）/ assistant（AI 回答）。
 * sourceRefs 存储 AI 回答引用的文档来源（JSON 格式的 SourceReference 列表），仅 assistant 消息有值。
 */
@Data
@TableName("t_chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    /** user 或 assistant */
    private String role;
    private String content;
    /** 引用来源列表（JSON），记录 RAG 检索命中的文档信息 */
    private String sourceRefs;
    private Integer tokensUsed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
