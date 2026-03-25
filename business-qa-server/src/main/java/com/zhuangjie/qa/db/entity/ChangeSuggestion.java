package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 变更建议实体，对应 t_change_suggestion 表。
 *
 * 由 SuggestionGenerator 调用 LLM 分析代码 diff 后生成。
 * 每条建议对应一个需要更新的文档章节。
 *
 * status 流转：PENDING → APPLIED（已应用）/ IGNORED（已忽略）
 */
@Data
@TableName("t_change_suggestion")
public class ChangeSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的变更检测记录 */
    private Long detectionId;
    /** 建议更新的文档 ID */
    private Long documentId;
    /** 受影响的章节描述 */
    private String affectedSection;
    /** 原始文本片段 */
    private String originalText;
    /** AI 建议的修改后文本 */
    private String suggestedText;
    /** 需要修改的原因说明 */
    private String reason;
    /** PENDING / APPLIED / IGNORED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
