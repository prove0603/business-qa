package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_module")
public class QaModule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String moduleName;
    private String moduleCode;
    private String gitRemoteUrl;
    private String gitBranch;
    private String moduleType;
    private String description;
    private String lastSyncCommit;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
