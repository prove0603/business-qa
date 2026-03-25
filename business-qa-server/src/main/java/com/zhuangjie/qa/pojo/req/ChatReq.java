package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 智能问答请求体。
 *
 * @param question  用户问题（必填）
 * @param moduleIds 检索范围的模块 ID 列表（可选，COMMON 模块会自动包含）
 * @param sessionId 对话会话 ID（可选，为空则自动创建新会话）
 */
public record ChatReq(
        @NotBlank String question,
        List<Long> moduleIds,
        Long sessionId
) {}
