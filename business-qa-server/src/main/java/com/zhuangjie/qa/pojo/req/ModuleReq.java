package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 模块创建/更新请求体。
 *
 * @param moduleName   模块名称（必填）
 * @param moduleCode   模块唯一编码（必填）
 * @param gitRemoteUrl Git 仓库远程 URL（可选，用于变更检测）
 * @param gitBranch    Git 分支（可选，默认 master）
 * @param moduleType   模块类型（必填）：COMMON 或 TASK
 * @param description  模块描述（可选）
 */
public record ModuleReq(
        @NotBlank String moduleName,
        @NotBlank String moduleCode,
        String gitRemoteUrl,
        String gitBranch,
        @NotBlank String moduleType,
        String description
) {}
