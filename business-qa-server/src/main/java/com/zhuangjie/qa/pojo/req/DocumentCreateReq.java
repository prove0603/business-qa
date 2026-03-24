package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentCreateReq(
        @NotNull Long moduleId,
        @NotBlank String title,
        @NotBlank String content,
        String fileType
) {}
