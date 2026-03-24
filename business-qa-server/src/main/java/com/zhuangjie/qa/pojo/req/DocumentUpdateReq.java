package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;

public record DocumentUpdateReq(
        @NotBlank String title,
        @NotBlank String content
) {}
