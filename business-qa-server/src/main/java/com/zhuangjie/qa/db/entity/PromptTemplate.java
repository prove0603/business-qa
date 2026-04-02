package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateName;
    private String templateKey;
    private String content;
    private String templateType;
    private String description;
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
