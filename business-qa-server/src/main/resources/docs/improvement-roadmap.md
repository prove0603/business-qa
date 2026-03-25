# Business QA 商用级提升路线图

> 基于对现有代码的完整审阅，按领域分类列出所有可提升点。  
> 每项标注了优先级（P0 最高）和预估工作量。

---

## 一、AI 对话能力提升

### 1.1 Prompt 工程化管理 `P0` `2-3天`

**现状**：Prompt 硬编码在 Java text block 中（`ChatService.streamChat()` 和 `AiChatConfig.SYSTEM_PROMPT`）。

```java
// ChatService.java:46
String augmentedQuestion = """
    基于以下参考文档回答用户的问题...
```

**目标**：
- 将所有 Prompt 抽取到 `resources/prompts/` 目录的 `.st` 模板文件中
- 支持变量注入（Spring AI 原生支持 StringTemplate）
- 支持运行时热更新（不重启切换 Prompt）
- 为后续 A/B 测试 Prompt 效果打基础

**参考**：Spring AI 的 `PromptTemplate` + `Resource` 加载机制，可参考 `interview-guide` 项目的 `resources/prompts/*.st` 实现。

---

### 1.2 Query Rewriting（查询改写） `P0` `1-2天`

**现状**：用户的原始问题直接用于向量检索。

```java
// ChatService.java:38
List<Document> docs = vectorService.search(question, effectiveModuleIds, 5);
```

**问题**：用户口语化的提问（如"那个功能怎么回事"）检索效果差。

**目标**：
- 在检索前，用 LLM 将用户问题改写为更适合检索的 query
- 结合对话历史做 Query Contextualization（如"它怎么用"→"XX功能怎么用"）
- 示例流程：
  ```
  用户问题 + 对话历史 → LLM 改写 → 改写后的 query → 向量检索
  ```

---

### 1.3 对话记忆持久化与优化 `P0` `2-3天`

**现状**：存在两套记忆系统，且互不同步。

```java
// AiChatConfig.java — 内存记忆（重启丢失）
ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();

// ChatHistoryService.java — DB 持久化（不回填给 Spring AI）
chatHistoryService.saveMessage(sessionId, "user", req.question(), null);
```

**问题**：
- 服务重启后，ChatMemory 清空，LLM 丢失上下文
- DB 里存了完整历史，但 Spring AI 的 Advisor 读不到

**目标**：
- 实现自定义 `ChatMemory`，底层读写 `t_chat_message` 表
- 或在每次请求前，从 DB 加载最近 N 条消息注入 ChatMemory
- 长对话时用 LLM 生成历史摘要（Summary Memory），控制 token 消耗

---

### 1.4 Function Calling / Tool Use `P1` `3-5天`

**现状**：未使用。LLM 只能基于检索到的文档回答。

**目标**：
- 让 LLM 自主决定何时调用工具，如：
  - `searchKnowledgeBase(query, moduleIds)` — 自主检索知识库
  - `listModules()` — 查看可用模块
  - `getDocumentDetail(docId)` — 获取完整文档
- 使用 Spring AI 的 `@Tool` 注解或 `FunctionCallback` 注册工具
- 这是从"问答助手"进化为"AI Agent"的关键一步

---

### 1.5 Structured Output（结构化输出） `P1` `1天`

**现状**：`SuggestionGenerator` 通过 Prompt 约束 AI 返回 JSON，手动解析。

```java
// SuggestionGenerator.java:101-128
String cleaned = aiResponse.trim();
if (cleaned.startsWith("```")) { ... }
JSONArray arr = JSONUtil.parseArray(cleaned);
```

**问题**：AI 返回格式不稳定，解析易失败。

**目标**：
- 使用 Spring AI 的 `BeanOutputConverter` / `entity()` 方法
- 定义 Java record 作为输出 schema，让框架自动处理 JSON schema 注入和解析
- 示例：
  ```java
  chatClient.prompt().user(prompt)
      .call()
      .entity(new ListOutputConverter<>(new BeanOutputConverter<>(Suggestion.class)));
  ```

---

### 1.6 多模型支持与路由 `P2` `2-3天`

**现状**：只对接了 DashScope 的 `qwen-plus`。

**目标**：
- 支持配置多个模型（OpenAI、Claude、本地 Ollama 等）
- 按场景路由：简单问题用快速/便宜的模型，复杂问题用强力模型
- 用 Strategy 模式封装，通过配置文件切换

---

### 1.7 Guardrails 安全护栏 `P1` `2-3天`

**现状**：无任何输入/输出安全检查。

**目标**：
- **输入侧**：Prompt Injection 检测（识别恶意注入指令的用户输入）
- **输出侧**：敏感信息过滤（个人信息、API Key 等不应出现在回答中）
- **回答限制**：拒绝回答与业务无关的问题（通过 System Prompt 或 Advisor 实现）
- 可用 Spring AI 的 Advisor 链实现前置/后置拦截

---

## 二、RAG 检索质量提升

### 2.1 相似度阈值过滤 `P0` `0.5天`

**现状**：返回 top-5 结果，不管相似度分数多低。

```java
// VectorService.java:59-60
var builder = SearchRequest.builder().query(query).topK(topK);
```

**问题**：不相关的文档也会被当作"参考资料"注入 Prompt，误导 LLM。

**目标**：
- 设置 `similarityThreshold(0.7)`（或可配置），过滤低分结果
- 当检索结果为空时，让 LLM 明确告知"未找到相关文档"

---

### 2.2 Hybrid Search（混合检索） `P1` `2-3天`

**现状**：纯向量语义检索。

**问题**：语义检索对专有名词、编号、精确匹配效果差。

**目标**：
- 结合全文检索（PostgreSQL `tsvector` / `ts_rank`）+ 向量检索
- 用 Reciprocal Rank Fusion (RRF) 或加权方式合并两路结果
- PgVector 本身支持在同一个 PostgreSQL 中实现

---

### 2.3 Re-ranking（重排序） `P1` `2天`

**现状**：直接使用向量检索的排序结果。

**目标**：
- 第一步用向量检索召回 top-20
- 第二步用 Cross-Encoder 或 LLM 对 top-20 重排序，取 top-5
- 显著提升检索精度，特别是在文档量大的时候

---

### 2.4 分块策略增强 `P1` `2-3天`

**现状**：两级分块（标题 → 段落），固定 1000 字符上限。

```java
// ChunkSplitter.java
private static final int MAX_CHUNK_SIZE = 1000;
private static final int MIN_CHUNK_SIZE = 50;
```

**目标**：
- **Overlapping（重叠分块）**：相邻 chunk 重叠 10-20%，避免关键信息被截断
- **Semantic Chunking**：基于语义相似度分块（相似的句子归为同一个 chunk）
- **按文件类型定制策略**：代码文件按函数/类分块，表格文件按行分块
- **分块参数可配置化**：从 `application.yml` 读取 `MAX_CHUNK_SIZE` 等参数

---

### 2.5 Metadata 丰富化 `P2` `1天`

**现状**：chunk 的 metadata 只有基础字段。

```java
// VectorService.java:36-43
Map<String, Object> metadata = Map.of(
    "document_id", doc.getId(),
    "module_id", module.getId(),
    "module_code", module.getModuleCode(),
    "module_type", module.getModuleType(),
    "doc_title", doc.getTitle(),
    "chunk_index", i
);
```

**目标**：增加以下元数据
- `heading_path`：chunk 所在的标题层级路径（如 "# 部署 / ## 环境配置"）
- `update_time`：文档最后更新时间（支持按时间过滤）
- `prev_chunk_id` / `next_chunk_id`：前后 chunk 关联（支持上下文扩展检索）
- `author`：文档作者

---

### 2.6 多轮对话检索优化 `P1` `1-2天`

**现状**：每轮对话都用用户原始问题检索，不考虑上下文。

**问题**：用户说"它的配置参数是什么"，"它"指代不明，检索效果差。

**目标**：
- 将多轮对话历史 + 当前问题一起发给 LLM，生成一个自包含的检索 query
- 示例：`["Redis 缓存怎么配置", "它支持集群吗"] → "Redis 缓存集群配置"`

---

### 2.7 向量更新增量化 `P2` `2-3天`

**现状**：文档更新时删除所有旧向量，重新向量化全文。

```java
// VectorService.java:30
deleteByDocumentId(doc.getId());  // 全删
// ... 重新分块、全部重新 embedding
```

**问题**：大文档改一行就全量重建，浪费 API 调用（embedding 按 token 计费）。

**目标**：
- 对比新旧分块，只对变更的 chunk 重新 embedding
- 基于 content hash 判断 chunk 是否变更

---

## 三、工程质量提升

### 3.1 统一异常处理 `P0` `1天`

**现状**：直接抛 `RuntimeException`，无错误码体系。

```java
// DocumentService.java:68
throw new RuntimeException("文件解析后无有效文本内容");
// DocumentService.java:103
throw new RuntimeException("Document not found: " + id);
```

**目标**：
- 自定义 `BusinessException(ErrorCode, message)`
- 全局 `@RestControllerAdvice` 统一处理
- 前端根据错误码展示友好提示

---

### 3.2 向量化失败重试机制 `P0` `1-2天`

**现状**：`@Async` 中 catch 异常只打日志，不重试。

```java
// VectorSyncService.java:50
} catch (Exception e) {
    log.error("Failed to vectorize document {}: {}", documentId, e.getMessage(), e);
}
```

**目标**：
- 文档表增加 `vector_status` 字段（PENDING / PROCESSING / SUCCESS / FAILED）
- 失败时记录失败原因和重试次数
- 定时任务扫描 FAILED 状态的文档，自动重试（最多 3 次）
- 管理后台可手动触发重试

---

### 3.3 单元测试 `P1` `3-5天`

**现状**：项目 0 个测试文件。

**目标**：至少覆盖以下核心模块
- `ChunkSplitter`：各种边界情况（空文档、超长文档、无标题文档、纯代码文档）
- `VectorService`：mock VectorStore，验证 metadata 构建和 filter 逻辑
- `ChatService`：mock ChatClient，验证 RAG prompt 拼接逻辑
- `DashScopeEmbeddingModel`：mock DashScope SDK，验证批处理和错误处理
- `SuggestionGenerator`：mock ChatClient，验证 JSON 解析逻辑

---

### 3.4 并发安全 `P1` `1天`

**现状**：异步向量化与同步更新无锁。

**问题**：快速连续编辑同一文档时，可能出现：
1. 编辑 A 触发向量化 A（异步）
2. 编辑 B 触发向量化 B（异步）
3. 向量化 A 完成，将过期的 chunk 写入向量库

**目标**：
- 向量化前检查文档 version，确保与触发时一致
- 或使用分布式锁（Redis / 数据库行锁），同一文档同时只能有一个向量化任务

---

### 3.5 接口幂等性 `P2` `0.5天`

**现状**：文档上传无去重。

**目标**：
- 基于 `contentHash` 去重，同一文件重复上传时返回已有文档而非新建
- 或至少提示用户"已存在相同内容的文档"

---

### 3.6 配置外部化 `P2` `0.5天`

**现状**：`ChunkSplitter` 的参数是编译时常量。

```java
private static final int MAX_CHUNK_SIZE = 1000;
private static final int MIN_CHUNK_SIZE = 50;
```

**目标**：
- 分块参数、top-K、similarity threshold、max-batch-size 等从 `application.yml` 读取
- 使用 `@ConfigurationProperties` 绑定

---

## 四、可观测性与运维

### 4.1 Token 用量追踪 `P0` `1-2天`

**现状**：未记录 token 消耗。`t_chat_message` 有 `tokens_used` 字段但从未写入。

**目标**：
- 从 ChatClient 的响应中提取 usage 信息（prompt_tokens, completion_tokens）
- 写入 `t_chat_message.tokens_used`
- 前端展示每次对话的 token 消耗
- 仪表盘展示 token 消耗趋势和成本估算

---

### 4.2 Embedding 调用监控 `P1` `1天`

**现状**：embedding 失败时返回空数组，上层不感知。

```java
// DashScopeEmbeddingModel.java:96-98
} catch (Exception e) {
    log.error("DashScope embedding call failed: {}", e.getMessage());
    return List.of();
}
```

**目标**：
- 记录 embedding 调用的成功率、延迟、token 消耗
- 失败时抛异常（而非静默返回空），让上层决定如何处理
- 加入重试机制（指数退避）
- 加入限流保护（DashScope 有 QPS 限制）

---

### 4.3 调用链追踪 `P2` `1-2天`

**现状**：基础 `log.info/error`。

**目标**：
- 引入 Trace ID，从 HTTP 请求到 LLM 调用全链路可追踪
- 记录每次 RAG 检索的耗时、返回结果数、相似度分数
- 记录每次 LLM 调用的耗时、token 数
- 可集成 Spring Boot Actuator + Micrometer

---

## 五、用户体验提升

### 5.1 回答反馈机制 `P1` `1-2天`

**现状**：无。

**目标**：
- 用户可对 AI 回答点赞/踩
- 存储反馈数据，用于分析 prompt 效果和检索质量
- 长期可用于微调或 RLHF

---

### 5.2 流式引用来源 `P1` `1天`

**现状**：引用在回答完成后才计算。

```java
// ChatController.java:42-44
.doOnComplete(() -> {
    var refs = chatService.extractSourceRefs(req.question(), req.moduleIds());
```

**问题**：`extractSourceRefs` 会再次调用 `vectorService.search()`，浪费一次检索。

**目标**：
- 在 `streamChat` 开始时就把检索结果缓存
- SSE 第一帧发送引用来源（或最后一帧发送特殊格式的引用数据）
- 前端边显示流式回答边展示引用

---

### 5.3 对话分支与重新生成 `P2` `2天`

**现状**：不支持。

**目标**：
- 用户可在某轮对话基础上重新提问（编辑已发送的消息）
- 支持"重新生成"当前回答

---

### 5.4 API 认证鉴权 `P1` `2-3天`

**现状**：所有接口无认证，裸露在外。

**目标**：
- 至少实现 JWT token 认证
- 支持多用户隔离（每个用户看到自己的对话和文档）
- 可集成 Spring Security

---

## 六、当前已知 Bug

### 6.1 向量删除过滤表达式语法错误 `P0` `0.5天`

**现状**：`VectorService.deleteByDocumentId()` 使用的过滤表达式在 Spring AI 1.0.0 中不兼容。

```java
// VectorService.java:73
vectorStore.delete("document_id == " + documentId);
```

**错误日志**：
```
FilterExpressionTextParser$FilterExpressionParseException: 
no viable alternative at input 'Expression['
```

**修复方向**：改用 `FilterExpressionBuilder` 构建表达式，或使用 Spring AI 1.0.0 支持的过滤语法。

---

## 提升路线建议

按阶段推进：

**Phase 1（1-2 周）— 修复 + 基础加固**
- [x] 修复向量删除 Bug（6.1）
- [ ] 相似度阈值过滤（2.1）
- [ ] 统一异常处理（3.1）
- [ ] Token 用量追踪（4.1）
- [ ] 向量化失败重试（3.2）

**Phase 2（2-4 周）— AI 能力提升**
- [ ] Prompt 工程化管理（1.1）
- [ ] Query Rewriting（1.2）
- [ ] 对话记忆持久化（1.3）
- [ ] Structured Output（1.5）
- [ ] 多轮对话检索优化（2.6）

**Phase 3（4-8 周）— 商用级进阶**
- [ ] Function Calling（1.4）
- [ ] Hybrid Search（2.2）
- [ ] Re-ranking（2.3）
- [ ] 分块策略增强（2.4）
- [ ] 安全护栏（1.7）
- [ ] API 认证鉴权（5.4）
- [ ] 单元测试（3.3）
