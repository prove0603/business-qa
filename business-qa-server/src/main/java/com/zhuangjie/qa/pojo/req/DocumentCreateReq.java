package com.zhuangjie.qa.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 在线创建文档请求体。
 *
 * @param moduleId 所属模块 ID（必填）
 * @param title    文档标题（必填）
 * @param content  文档内容（必填，通常为 Markdown 格式）
 * @param fileType 文件类型（可选，默认 MARKDOWN）
 */
public record DocumentCreateReq(
        @NotNull Long moduleId,
        @NotBlank String title,
        @NotBlank String content,
        String fileType
) {}
