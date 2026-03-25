package com.zhuangjie.qa.pojo.vo;

/**
 * 仪表盘概览数据。
 *
 * @param moduleCount            有效模块总数
 * @param documentCount          文档总数
 * @param chatSessionCount       对话会话总数
 * @param pendingSuggestionCount 待处理的 AI 建议数量
 */
public record DashboardVo(
        long moduleCount,
        long documentCount,
        long chatSessionCount,
        long pendingSuggestionCount
) {}
