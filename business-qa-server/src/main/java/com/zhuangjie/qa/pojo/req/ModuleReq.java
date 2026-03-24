package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;

public record ModuleReq(
        @NotBlank String moduleName,
        @NotBlank String moduleCode,
        String gitRemoteUrl,
        String gitBranch,
        @NotBlank String moduleType,
        String description
) {}
