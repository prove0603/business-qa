package com.zhuangjie.qa.pojo.vo;

public record DashboardVo(
        long moduleCount,
        long documentCount,
        long chatSessionCount,
        long pendingSuggestionCount
) {}
