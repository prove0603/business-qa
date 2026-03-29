package com.zhuangjie.qa.mcpserver.config;

import com.zhuangjie.qa.mcpserver.tool.BusinessQaMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerToolConfig {

    @Bean
    public ToolCallbackProvider businessQaToolProvider(BusinessQaMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
