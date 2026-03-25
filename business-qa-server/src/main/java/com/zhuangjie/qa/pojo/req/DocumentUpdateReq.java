package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 文档更新请求体。
 *
 * @param title   文档标题（必填）
 * @param content 文档内容（必填）
 */
public record DocumentUpdateReq(
        @NotBlank String title,
        @NotBlank String content
) {}
