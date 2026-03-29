package com.zhuangjie.qa.mcpserver.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BusinessQaMcpTools {

    private static final Map<String, String> KNOWLEDGE = Map.of(
            "rag", "RAG = Retrieval Augmented Generation。推荐流程：检索 -> 组装上下文 -> LLM 生成 -> 结果回写。",
            "chatclient", "Spring AI ChatClient 用于构建对话调用链，可叠加 memory、advisors、tools。",
            "mcp", "MCP 用于让模型以标准协议调用外部工具/资源，适合把业务能力暴露给 AI。"
    );

    @Tool(description = "根据关键词检索 business-qa 的示例知识点")
    public String searchKnowledge(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "请输入关键词，例如：rag、chatclient、mcp";
        }

        String normalized = keyword.toLowerCase();
        String result = KNOWLEDGE.entrySet().stream()
                .filter(entry -> entry.getKey().contains(normalized)
                        || entry.getValue().toLowerCase().contains(normalized))
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));

        return result.isBlank() ? "未命中示例知识，请尝试关键词：rag / chatclient / mcp" : result;
    }

    @Tool(description = "返回当前服务器时间，便于验证 MCP 工具可调用")
    public String currentServerTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(description = "计算两个数字相加结果")
    public String add(double a, double b) {
        return "计算结果: " + (a + b);
    }
}
