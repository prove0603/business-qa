package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_guardrail_rule")
public class GuardrailRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;
    private String ruleType;
    private String pattern;
    private String action;
    private String replyMessage;
    private String description;
    private Integer sortOrder;
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
