package com.zhuangjie.qa.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolSupport {

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    private final Environment environment;

    public ToolCallback[] getToolCallbacks() {
        if (!isClientEnabled()) {
            return new ToolCallback[0];
        }

        ToolCallback[] callbacks = toolCallbackProviders.orderedStream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toArray(ToolCallback[]::new);

        if (callbacks.length == 0) {
            log.warn("MCP client enabled, but no tool callbacks discovered.");
        } else {
            log.info("MCP tool callbacks loaded: {}", callbacks.length);
        }
        return callbacks;
    }

    private boolean isClientEnabled() {
        return environment.getProperty("spring.ai.mcp.client.enabled", Boolean.class, false);
    }
}
