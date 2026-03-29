package com.zhuangjie.qa.controller;

import com.zhuangjie.qa.chat.McpToolSupport;
import com.zhuangjie.qa.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpToolSupport mcpToolSupport;
    private final Environment environment;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        ToolCallback[] callbacks = mcpToolSupport.getToolCallbacks();
        boolean enabled = environment.getProperty("spring.ai.mcp.client.enabled", Boolean.class, false);
        return Result.ok(Map.of(
                "clientEnabled", enabled,
                "toolCount", callbacks.length
        ));
    }
}
