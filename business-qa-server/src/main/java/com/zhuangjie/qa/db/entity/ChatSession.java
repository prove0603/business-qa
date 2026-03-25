package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体，对应 t_chat_session 表。
 * 一个会话包含多条消息（ChatMessage），代表一次完整的问答对话。
 *
 * moduleFilter 存储 JSON 格式的模块 ID 列表，记录用户发起对话时选择的检索范围。
 */
@Data
@TableName("t_chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    /** 用户选择的模块 ID 列表（JSON 格式，如 [1,2,3]） */
    private String moduleFilter;
    private Integer messageCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
