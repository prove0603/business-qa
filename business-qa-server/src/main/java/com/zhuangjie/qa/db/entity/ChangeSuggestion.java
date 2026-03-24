package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_change_suggestion")
public class ChangeSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long detectionId;
    private Long documentId;
    private String affectedSection;
    private String originalText;
    private String suggestedText;
    private String reason;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
