# Business QA 代码阅读指南

> 不要逐行读代码。按数据流追踪主线，抓核心节点，忽略 CRUD 模板代码。

---

## 一、全局认知（第一步，30 分钟）

### 1.1 先看配置文件，建立全局地图


| 文件                  | 你能了解到什么                                                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `application.yml`   | 项目用了哪些中间件（PostgreSQL、MinIO、DashScope）、AI 模型是 `qwen-plus`、Embedding 模型是 `text-embedding-v3`、RAG 功能通过 `qa.rag.enabled` 开关控制 |
| `schema.sql`        | 6 张表的结构：模块、文档、变更检测、变更建议、对话会话、对话消息。外加 PgVectorStore 自动建的 `qa_vector` 向量表                                                   |
| `AiChatConfig.java` | ChatClient 怎么构建的、System Prompt 是什么、ChatMemory 用的 MessageWindowChatMemory（最近 20 条）、有两个 ChatClient Bean（聊天用 + 代码分析用）        |
| `RagConfig.java`    | VectorStore 的核心配置：PgVectorStore + HNSW 索引 + 余弦距离 + 1024 维                                                                 |


### 1.2 画出核心数据流向

```
用户 ──→ Vue 前端 ──→ REST Controller ──→ Service 层 ──→ LLM/向量库/MinIO/PostgreSQL
         │                                                    │
         │◄───── SSE 流式 ─────────────────────────────────────┘
```

数据存储分三层：

- **PostgreSQL**：业务数据（模块、文档元数据、对话记录、变更检测）
- **PgVector（qa_vector 表）**：向量索引，同一个 PostgreSQL 实例
- **MinIO**：文件原件存储（PDF/Word 等）

---

## 二、核心流程追踪（第二步，3 条主线）

### 主线 1：RAG 智能问答（最重要）

这是整个项目的核心价值。按调用链从上往下读：

```
ChatController.streamChat()                    ← 入口：接收用户提问
  │
  ├─ ChatHistoryService.createSession()        ← 首次对话自动建会话
  ├─ ChatHistoryService.saveMessage("user")    ← 存用户消息到 DB
  │
  └─ ChatService.streamChat()                  ← 核心 RAG 逻辑
       │
       ├─ buildEffectiveModuleIds()            ← 合并 COMMON 模块 + 用户选的模块
       │
       ├─ VectorService.search()               ← ★ 向量检索
       │    └─ SearchRequest.builder()
       │         .query(question)              ← 用户原始问题作为 query
       │         .topK(5)                      ← 取最相似的 5 个 chunk
       │         .filterExpression(...)        ← 按 module_id 过滤
       │         → vectorStore.similaritySearch()
       │
       ├─ 拼接 augmentedQuestion               ← ★ RAG Prompt 构建
       │    "基于以下参考文档回答用户的问题..."
       │    + 检索到的文档内容
       │    + 用户原始问题
       │
       └─ chatClient.prompt()                  ← ★ LLM 调用
            .user(augmentedQuestion)
            .advisors(CONVERSATION_ID)         ← 注入会话记忆
            .stream().content()                ← SSE 流式返回
```

**核心要点**：

1. **检索与生成分离**：先检索（VectorService），再拼接（text block），再生成（ChatClient）
2. **Advisor 模式**：ChatMemory 不是手动管理，而是通过 `MessageChatMemoryAdvisor` 自动注入历史消息
3. **流式输出**：`Flux<String>` + `doOnComplete` 在回答完成后异步保存助手消息

---

### 主线 2：文档上传 → 向量化

这是 RAG 的数据供给侧。按调用链从上往下读：

```
DocumentController.upload()                    ← 入口：接收文件
  │
  └─ DocumentService.uploadFile()
       │
       ├─ ContentTypeDetectionService          ← 校验文件合法性（Tika 检测 MIME）
       │    .validateFile()
       │
       ├─ DocumentParseService.parseContent()  ← ★ 文本提取
       │    └─ Apache Tika AutoDetectParser
       │         → BodyContentHandler          ← 提取纯文本
       │         → TextCleaningService         ← 清洗多余空白
       │
       ├─ FileStorageService.uploadDocument()  ← 原件存 MinIO
       │    └─ S3Client.putObject()
       │
       ├─ documentDbService.save()             ← 元数据 + 解析文本存 PostgreSQL
       │
       └─ VectorSyncService.asyncVectorize()   ← ★ 异步向量化
            │  (@Async 在线程池中执行)
            │
            └─ VectorService.vectorizeDocument()
                 │
                 ├─ deleteByDocumentId()       ← 先删旧向量
                 │
                 ├─ ChunkSplitter.split()      ← ★ 文本分块
                 │    ├─ splitByHeadings()      ← 先按 Markdown # 标题切
                 │    └─ splitBySize(1000)      ← 超长段落按 1000 字符切
                 │
                 ├─ 为每个 chunk 构造 metadata  ← document_id, module_id, doc_title 等
                 │
                 └─ vectorStore.add(documents)  ← ★ 调用 EmbeddingModel 生成向量 → 存入 PgVector
                      │
                      └─ DashScopeEmbeddingModel.call()
                           └─ DashScope SDK TextEmbedding API
                                (text-embedding-v3, 1024 维, 批量 10 条)
```

**核心要点**：

1. **管道式处理**：校验 → 解析 → 存储 → 向量化，每一步职责单一
2. **异步解耦**：`@Async` 让向量化不阻塞上传接口返回
3. **自定义 EmbeddingModel**：`DashScopeEmbeddingModel` 适配了 Spring AI 接口，底层用 DashScope SDK
4. **分块策略**：两级分块（标题 → 段落/大小），是 RAG 质量的关键因素

---

### 主线 3：Git 变更检测 → AI 建议

这是项目的特色功能，值得研究另一种 AI 使用模式（非 RAG）：

```
ChangeController.detect(moduleId)              ← 入口：触发检测
  │
  └─ ChangeDetector.detect()
       │
       ├─ GitSyncService.syncRepo()            ← JGit clone/pull 代码
       ├─ GitSyncService.resolveHead()         ← 获取最新 commit hash
       │
       ├─ GitSyncService.detectChanges()       ← ★ 对比两个 commit 的 diff
       │    └─ JGit DiffFormatter              ← 输出变更文件列表
       │
       └─ SuggestionGenerator                  ← ★ AI 分析 diff
            .generateSuggestions()
            │  (@Async 异步执行)
            │
            ├─ 加载模块下所有文档               ← 作为 AI 上下文
            ├─ 截断 diff（最大 8000 字符）
            │
            ├─ analysisChatClient.prompt()      ← ★ 用独立的 ChatClient（无记忆）
            │    .user(ANALYSIS_PROMPT)          ← 要求 AI 返回 JSON 数组
            │    .call().content()               ← 同步调用（非流式）
            │
            └─ parseSuggestionsAndSave()         ← 解析 AI 返回的 JSON，存入建议表
```

**核心要点**：

1. **两个 ChatClient**：聊天用带记忆的，代码分析用无记忆的 `analysisChatClient`
2. **Structured Output**：通过 Prompt 约束 AI 返回 JSON 格式（目前是手动解析，非 Spring AI 的结构化输出）
3. **JGit 集成**：纯 Java Git 操作，不依赖本地 git 命令

---

## 三、Spring AI 核心概念对照表（第三步）

读代码时对照此表理解 Spring AI 的设计：


| Spring AI 概念       | 本项目对应代码                                        | 作用                                  |
| ------------------ | ---------------------------------------------- | ----------------------------------- |
| `ChatModel`        | Spring Boot 自动配置（`spring.ai.openai.`*）         | LLM 的底层连接，一般不直接用                    |
| `ChatClient`       | `AiChatConfig.chatClient()`                    | 高级对话客户端，支持 System Prompt、Advisor、流式 |
| `Advisor`          | `MessageChatMemoryAdvisor`                     | 拦截器模式，在请求前后自动注入/保存对话历史              |
| `ChatMemory`       | `MessageWindowChatMemory`（最近 20 条）             | 对话记忆存储，当前是内存实现                      |
| `EmbeddingModel`   | `DashScopeEmbeddingModel`                      | 文本 → 向量的转换器                         |
| `VectorStore`      | `PgVectorStore`（HNSW, cosine, 1024d）           | 向量存储 + 相似性检索                        |
| `SearchRequest`    | `VectorService.search()` 中构建                   | 封装检索参数：query、topK、filter            |
| `FilterExpression` | `FilterExpressionBuilder.in("module_id", ...)` | 向量检索的元数据过滤条件                        |
| `Document`         | `VectorService.vectorizeDocument()` 中构建        | Spring AI 的文档抽象，包含 text + metadata  |


---

## 四、值得深入研究的"连接点"

以下是代码中不直观但很关键的机制，建议重点研究：

### 4.1 ChatClient 的 Advisor 链

```java
chatClient.prompt()
    .user(augmentedQuestion)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
    .stream().content();
```

- `MessageChatMemoryAdvisor` 会在发送请求前，从 `ChatMemory` 中取出该 conversationId 的历史消息，追加到 messages 列表
- 请求返回后，自动把新的 user + assistant 消息存回 `ChatMemory`
- **但注意**：本项目的 `ChatMemory` 是内存实现，重启后丢失；而 `ChatHistoryService` 存到了 DB，两套记忆系统并存

### 4.2 PgVectorStore 的自动建表

```java
PgVectorStore.builder(...)
    .initializeSchema(true)  // 启动时自动创建 qa_vector 表 + HNSW 索引
```

- 表结构：`id (UUID)`, `content (TEXT)`, `metadata (JSONB)`, `embedding (vector(1024))`
- HNSW 索引加速近似最近邻搜索

### 4.3 DashScope 的双通道对接

- **ChatModel**：通过 `spring.ai.openai.`* 配置，走 OpenAI 兼容接口 → DashScope
- **EmbeddingModel**：通过 DashScope SDK 直连（因为 Spring AI 的 OpenAI Embedding 不兼容 DashScope 的维度参数）
- 两个通道用同一个 API Key

---

## 五、代码文件阅读优先级

按重要性排序，标注了建议的阅读时间：


| 优先级 | 文件                             | 读什么                                 | 时间    |
| --- | ------------------------------ | ----------------------------------- | ----- |
| ★★★ | `ChatService.java`             | RAG 拼接逻辑、ChatClient 调用方式            | 15min |
| ★★★ | `AiChatConfig.java`            | ChatClient 构建、Advisor、System Prompt | 10min |
| ★★★ | `VectorService.java`           | 向量化 + 检索的完整实现                       | 15min |
| ★★★ | `ChunkSplitter.java`           | 分块算法                                | 15min |
| ★★☆ | `DashScopeEmbeddingModel.java` | 自定义 EmbeddingModel 适配器              | 15min |
| ★★☆ | `RagConfig.java`               | PgVectorStore 配置                    | 5min  |
| ★★☆ | `VectorSyncService.java`       | 异步向量化编排                             | 10min |
| ★★☆ | `SuggestionGenerator.java`     | LLM 的另一种用法：结构化输出                    | 15min |
| ★☆☆ | `ChatController.java`          | SSE 流式接口                            | 5min  |
| ★☆☆ | `DocumentService.java`         | 文档 CRUD 编排                          | 10min |
| ★☆☆ | `DocumentParseService.java`    | Tika 文档解析                           | 5min  |
| ☆☆☆ | `ChatHistoryService.java`      | 对话历史 CRUD（模板代码）                     | 跳过    |
| ☆☆☆ | `db/entity/*.java`             | MyBatis-Plus 实体（模板代码）               | 跳过    |
| ☆☆☆ | `db/service/*.java`            | MyBatis-Plus Service（模板代码）          | 跳过    |
| ☆☆☆ | `file/FileStorageService.java` | S3 上传下载（通用代码）                       | 跳过    |


