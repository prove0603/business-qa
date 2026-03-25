package com.zhuangjie.qa.db.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模块实体，对应 t_module 表。
 *
 * 模块是文档的组织容器和问答的检索范围单元。
 *
 * 关键字段：
 * - moduleType: COMMON（全局模块，总是被检索）或 TASK（业务模块，需用户选择）
 * - gitRemoteUrl / gitBranch: 关联的 Git 仓库信息，用于变更检测功能
 * - lastSyncCommit: 上次 Git 同步时的 commit hash，作为下次 diff 的基准
 */
@Data
@TableName("t_module")
public class QaModule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String moduleName;
    /** 模块唯一编码，也用于生成 Git 本地克隆目录名 */
    private String moduleCode;
    /** Git 远程仓库 URL（可为空，不启用变更检测） */
    private String gitRemoteUrl;
    private String gitBranch;
    /** COMMON: 全局模块 / TASK: 业务模块 */
    private String moduleType;
    private String description;
    /** 上次 Git 同步的 commit hash，用于下次 diff 对比 */
    private String lastSyncCommit;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
