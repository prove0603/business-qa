很好的问题，这恰恰是理解 MCP/Tool Calling 最关键的一点。让我把完整的数据流和调用链路拆开讲清楚。

## 核心答案：LLM 不需要知道"MCP"这三个字

提示词里不需要出现"MCP"，因为 **MCP 是 Client 和 Server 之间的通信协议**，LLM 根本不参与这层通信。LLM 看到的是 **Tool/Function 的描述**，这是 Spring AI 框架在发给 LLM 的请求里自动拼上去的。

让我用实际代码把完整链路拆解一遍。

---

## 完整调用链路

### 阶段一：启动时（business-qa 启动）

```
business-qa 启动
    │
    ▼
Spring AI MCP Client 自动装配（读 application.yml 的 mcp.client 配置）
    │
    ▼ HTTP POST http://localhost:8090/mcp
    │ {"method": "initialize", ...}    ← JSON-RPC 握手
    │
    ▼ HTTP POST http://localhost:8090/mcp
    │ {"method": "tools/list"}          ← 拉取工具清单
    │
    ▼ sentinel-ai 返回 5 个工具的定义（名称 + 描述 + 参数 JSON Schema）
    │
    ▼
自动注册为 ToolCallbackProvider Bean（Spring 容器里）
    │
    ▼
AiChatConfig.chatClient() 方法执行：
    toolCallbackProviders.forEach(builder::defaultToolCallbacks)
    │
    ▼
chatClient 内部持有了 5 个工具的 ToolCallback
```

启动完成后，`chatClient` 就同时具备了 RAG 能力和 MCP 工具调用能力。

### 阶段二：用户提问时

假设用户调用：

```json
POST /api/chat/stream
{"question": "有哪些SQL存在性能风险？", "moduleIds": [1]}
```

#### 第 1 步：Controller -> ChatService

```
ChatController.streamChat()
    │
    ▼
ChatService.streamChat("有哪些SQL存在性能风险？", [1], sessionId)
```

#### 第 2 步：ChatService 做 RAG 检索

```java
// ChatService.java 第38行
List<Document> docs = vectorService.search(question, effectiveModuleIds, 5);
```

检索出相关文档，拼成上下文：

```
augmentedQuestion = """
    基于以下参考文档回答用户的问题...
    ## 参考文档
    （检索到的文档内容）
    ## 用户问题
    有哪些SQL存在性能风险？
"""
```

#### 第 3 步：发给 LLM — 这里是关键

```java
// ChatService.java 第60行
chatClient.prompt()
    .user(augmentedQuestion)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
    .stream()
    .content();
```

**这一步，Spring AI 框架实际发给 LLM（qwen-plus）的 HTTP 请求长这样**：

```json
POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions

{
  "model": "qwen-plus",
  "messages": [
    {
      "role": "system",
      "content": "你是一个智能业务助手，具备以下能力：..."
    },
    {
      "role": "user",
      "content": "基于以下参考文档回答...有哪些SQL存在性能风险？"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "listRiskySql",
        "description": "获取所有被标记为有风险的SQL列表，支持按风险等级...",
        "parameters": {
          "type": "object",
          "properties": {
            "riskLevel": {
              "type": "string",
              "description": "风险等级过滤，可选值: P0(致命)..."
            },
            "handleStatus": {
              "type": "string",
              "description": "处理状态过滤..."
            },
            "limit": {
              "type": "integer",
              "description": "返回条数限制..."
            }
          }
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "getSqlAnalysisDetail",
        "description": "获取某条SQL分析的完整详情..."
      }
    },
    {
      "type": "function",
      "function": {
        "name": "getSqlRiskOverview",
        "description": "获取项目整体的SQL风险概览统计..."
      }
    }
    // ... 另外2个工具
  ],
  "stream": true
}
```

**这就是答案** — `tools` 字段是 Spring AI 框架自动拼上去的，不是你在提示词里写的。LLM 收到的请求同时包含：
- `messages`：你的提示词 + 用户问题
- `tools`：从 MCP Server 拉取的所有工具描述

LLM 不知道"MCP"的存在，它只知道"我现在有几个 function 可以调用"。**`tools` 字段是 OpenAI 兼容 API 的标准能力**，qwen-plus 原生支持。

#### 第 4 步：LLM 决定调用工具

LLM 看到用户问的是"SQL性能风险"，又看到有个 `listRiskySql` 工具正好干这事，于是返回：

```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "listRiskySql",
          "arguments": "{}"
        }
      }]
    },
    "finish_reason": "tool_calls"
  }]
}
```

注意：**LLM 没有直接回答用户**，而是说"我想调用 `listRiskySql` 这个函数"。

#### 第 5 步：Spring AI 拦截 tool_calls，走 MCP 协议

```
Spring AI 框架收到 LLM 返回的 tool_calls
    │
    ▼ 查找名为 "listRiskySql" 的 ToolCallback
    │ （这个 ToolCallback 是 MCP Client 注册的）
    │
    ▼ MCP Client 发 HTTP 请求给 sentinel-ai
      POST http://localhost:8090/mcp
      {
          "jsonrpc": "2.0",
          "method": "tools/call",
          "params": {
              "name": "listRiskySql",
              "arguments": {}
          }
      }
    │
    ▼ sentinel-ai MCP Server 收到
    │ 路由到 SqlAnalysisMcpTools.listRiskySql()
    │ 查数据库，返回结果
    │
    ▼ HTTP Response 返回给 business-qa
      {
          "jsonrpc": "2.0",
          "result": {
              "content": [{
                  "type": "text",
                  "text": "[{analysisId:1, riskLevel:\"P0\", sqlText:\"SELECT * FROM ...\", ...}]"
              }]
          }
      }
```

**这一步对 LLM 和用户都是透明的**。用户不知道 MCP，LLM 也不知道 MCP。Spring AI 做了中间翻译：
- LLM 说"调用 listRiskySql"→ Spring AI 翻译成 MCP 协议 → sentinel-ai 执行 → 返回数据

#### 第 6 步：Spring AI 把工具结果送回 LLM

Spring AI 自动发第二次 LLM 请求：

```json
{
  "model": "qwen-plus",
  "messages": [
    {"role": "system", "content": "你是一个智能业务助手..."},
    {"role": "user", "content": "有哪些SQL存在性能风险？"},
    {"role": "assistant", "content": null, "tool_calls": [{"function": {"name": "listRiskySql"}}]},
    {"role": "tool", "tool_call_id": "call_abc123", "content": "[{analysisId:1, riskLevel:\"P0\", sqlText:\"SELECT * FROM orders WHERE...\", aiAnalysis:\"全表扫描...\"}]"}
  ]
}
```

LLM 现在拿到了真实数据，生成自然语言回答。

#### 第 7 步：LLM 返回最终回答

```
"根据Sentinel AI的分析结果，当前存在以下高风险SQL：

1. **P0 - 致命风险**
   SQL: `SELECT * FROM orders WHERE create_time > ...`
   来源: OrderMapper.xml
   问题: 全表扫描，缺少 create_time 索引
   建议: 添加索引 idx_orders_create_time

2. ..."
```

这个回答通过 SSE 流式返回给用户。

---

## 总结：谁知道什么

| 角色 | 知道 MCP 吗 | 知道工具吗 | 做了什么 |
|------|------------|-----------|---------|
| **用户** | 不知道 | 不知道 | 正常调用 `/api/chat/stream` |
| **ChatService** | 不知道 | 不知道 | 正常做 RAG，调 `chatClient` |
| **chatClient (Spring AI)** | 知道 | 知道 | 把工具描述拼到 `tools` 字段发给 LLM；拦截 `tool_calls` 走 MCP 协议 |
| **LLM (qwen-plus)** | 不知道 | 知道（通过 `tools` 字段） | 判断要不要调工具，返回 `tool_calls` 或直接回答 |
| **sentinel-ai** | 知道 | 提供工具 | 收到 MCP 请求，执行查询，返回数据 |

一句话：**LLM 是通过请求里的 `tools` 字段知道有工具可用的，不是通过提示词。提示词只是告诉 LLM "你有这个能力"作为引导，真正的工具描述是框架自动注入的。**