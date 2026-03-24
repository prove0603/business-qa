package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatReq(
        @NotBlank String question,
        List<Long> moduleIds,
        Long sessionId
) {}
