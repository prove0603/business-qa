# Java 程序员拥抱 AI — 业务问答助手

> 这是一个系列文章的第一篇。我打算用这个系列记录自己作为 Java 后端开发，从零开始学习和落地 AI 应用的完整过程。后续每篇文章会深入某个具体维度——比如怎么做对话记忆、检索方案怎么一步步演进、为什么要加护栏机制——包括踩过的坑和做出选择时的思考。这篇先做一个全局的介绍，让你知道这个项目是什么、做了什么、用了哪些技术。

**项目地址**：[https://github.com/prove0603/business-qa](https://github.com/prove0603/business-qa)

## 为什么做这个项目

说实话，AI 这波浪潮来得太快，作为写了几年 Java 的后端开发，焦虑是真的有。看着各种大模型、RAG、Agent 的概念满天飞，光看文章和教程总觉得隔了一层——不自己动手写一遍，很多细节根本想不到。

所以我给自己找了一个实际的场景：**业务知识问答**。我们日常工作中有大量的业务文档，新人入职要翻、日常开发要查、跨团队沟通要对。如果能把这些文档喂给 AI，用自然语言提问就能得到准确回答，这件事本身就是有价值的。

基于这个想法，我做了 **Business QA**——一个基于 RAG 的业务智能问答系统。它不是一个 demo 或者 toy project，而是一个有前后端、有完整功能链路、可以实际跑起来的应用。在做的过程中，我逐步接触到了 Embedding、向量检索、Prompt 工程、对话记忆、AI 安全等一系列话题，每个都值得单独拿出来聊。

## 这个系统能做什么

简单来说：你上传业务文档，系统帮你分块、向量化，然后你用自然语言提问，AI 基于文档内容回答，并标注引用来源。

但实际要把这件事做好，远不止"调个 API"这么简单。下面是目前系统的主要功能：

### 智能对话

核心功能。支持多轮对话，流式输出（打字机效果），回答带引用来源，可以按业务模块过滤检索范围。

![对话页面](images/chat.png)

左侧是历史会话列表，右上角可以按模块筛选知识范围，主区域是对话内容。可以看到 AI 的回答里引用了具体的文档内容，底部有"引用来源"折叠面板。

### 文档与模块管理

文档支持在线编辑（Markdown）和文件上传（PDF、Word、TXT）。上传后自动解析内容、分块、向量化。模块用来按业务维度归类文档，比如"数据服务"是通用模块，"案件管理"是任务模块。

![文档管理](images/documents.png)

![模块管理](images/modules.png)

### Prompt 模板管理

系统提示词不是硬编码在代码里的，而是存在数据库中，可以在页面上直接编辑。这样调整 AI 的回答风格、添加引用规则之类的事情，不用改代码重新部署。

![提示词管理](images/prompts.png)

### AI 护栏

输入侧有 Prompt 注入防护（防止用户通过提问绕过系统设定）和敏感词拦截；输出侧会自动对手机号、身份证号等敏感信息做脱敏处理。规则在线管理，实时生效，还带了个测试功能可以验证规则是否符合预期。

![护栏规则](images/guardrails.png)

### 其他

- **仪表盘**：模块数、文档数、对话数等基本统计
- **Git 变更检测**：关联 Git 仓库，代码变更后 AI 自动给出文档修改建议
- **MCP 工具集成**：通过 MCP 协议连接另一个项目 sentinel-ai，让 AI 在对话中可以直接调用 SQL 分析工具

## 技术选型与 AI 维度

这个项目涉及到的 AI 相关维度不少。之所以说"维度"而不是"功能"，是因为每一项都不是简单的开关，而是有一系列的技术选择和迭代空间。

### Spring AI

选 Spring AI 是因为我的技术栈就是 Java + Spring Boot，这是目前 Java 生态里最成熟的 AI 集成框架。它提供了统一的 `ChatClient` 抽象，底层可以对接 OpenAI、DashScope（百炼）等各种模型供应商。项目里用的是 Spring AI 1.1.4，通过 OpenAI 兼容接口对接阿里云百炼的 qwen3.5-plus 模型。

选 Spring AI 而不是自己手写 HTTP 调用，一个很重要的原因是它的 **Advisor 机制**——可以把 RAG、对话记忆、重试等逻辑以插件的形式挂载到调用链上，代码结构非常清晰。这部分后面会详细讲。

### Embedding

Embedding 是 RAG 的基础——把文本转成向量才能做语义检索。项目里用的是百炼的 text-embedding-v3 模型，1024 维。一开始我自己写了一个 `DashScopeEmbeddingModel` 适配器直接调 DashScope SDK，后来升级 Spring AI 版本后发现框架自带的 `OpenAiEmbeddingModel` 通过兼容接口就能直接用，就把自写的删掉了。这个演进过程会在后续文章里聊。

### RAG 注入

RAG（检索增强生成）是整个系统的核心模式。用户提问后，系统先从向量库中检索相关文档片段，把它们注入到 Prompt 的上下文中，再交给大模型回答。Spring AI 提供了 `RetrievalAugmentationAdvisor`，我在它的基础上配置了查询改写和混合检索。

一开始我是手动拼接检索到的文档到 Prompt 里的，后来迁移到了 Advisor 模式，代码简洁了很多，而且检索、改写、注入的流程都可以独立替换。

### 检索方式

检索是 RAG 效果好不好的关键。目前用的是**混合检索**——向量语义检索和 PG 全文关键词检索（BM25）双路并行，再通过 RRF（Reciprocal Rank Fusion）做融合排序。

为什么不只用向量检索？因为纯向量检索对关键词精确匹配不够敏感。比如用户问"CaseTransService.generateCase 方法做了什么"，这种包含精确类名的查询，BM25 反而更靠谱。两路结合，互相补位。

### 分块

文档怎么切分成块，直接影响检索质量。我的 `ChunkSplitter` 经过一次重写——从最初简单的"按标题+段落分割"，演进到了**递归分割+重叠窗口**：按分隔符优先级（Markdown 标题 → 空行 → 句号 → 换行）逐级递归，相邻块之间保留 150 字符的重叠。

为什么要重叠？因为不重叠的话，关键信息可能正好落在两个块的边界上，两个块各拿到半句话，检索时哪个都匹配不好。

### 对话记忆

多轮对话需要记忆上下文，但对话越长，塞进 Prompt 的历史消息越多，token 消耗就越大。最初用的是 Spring AI 自带的 `MessageWindowChatMemory`，简单粗暴地保留最近 N 条消息。后来我实现了 `SummarizingChatMemory`——当历史消息超过阈值时，用 LLM 把旧消息压缩成一段摘要，既保留了上下文语义，又控制了 token 开销。

### 安全防护

AI 应用不能裸奔。目前做了三层防护：

- **输入护栏**：拦截 Prompt 注入攻击和敏感词
- **输出护栏**：对 AI 输出中的手机号、身份证号等做自动脱敏
- **弹性保护**：Resilience4j 的限流（RateLimiter）防止请求打爆 AI 接口，熔断（CircuitBreaker）在 AI 服务连续出错时快速失败，LlmRetryAdvisor 做指数退避重试

这些不算"AI 功能"，但在实际落地中不可或缺。

### Prompt 管理

系统提示词决定了 AI 的行为模式。与其硬编码在代码里，不如做成可配置的。项目里的 Prompt 存在 `t_prompt_template` 表中，启动时从 DB 加载，可以在管理页面直接编辑。

### MCP

MCP（Model Context Protocol）是 Spring AI 支持的工具调用协议。business-qa 作为 MCP Client，通过 Streamable HTTP 连接另一个项目 sentinel-ai 的 MCP Server，获取 SQL 分析工具。LLM 在对话中会根据用户问题自动判断是否需要调用工具——这就是 Function Calling 的落地方式。

## 整体技术栈

| 层面 | 技术 |
|---|---|
| 后端 | Java 21, Spring Boot 3.5.x, Spring AI 1.1.4, Resilience4j 2.2.0 |
| AI 模型 | DashScope（百炼）qwen3.5-plus，OpenAI 兼容接口 |
| Embedding | text-embedding-v3（1024 维） |
| 向量库 | PostgreSQL + pgvector（HNSW 索引，余弦距离） |
| ORM | MyBatis-Plus 3.5.7 |
| 文件存储 | MinIO + Apache Tika |
| 前端 | Vue 3 + TypeScript + Element Plus + Vite 6 |

## 请求完整链路

一次用户提问在系统内部的完整流转路径：

```
用户提问
  → RateLimiter 限流
  → 输入护栏（Prompt 注入防护、敏感词拦截）
  → 引用预检索（sourceRefs）
  → ChatClient.stream()
    → LlmRetryAdvisor（指数退避重试）
    → RetrievalAugmentationAdvisor:
      → RewriteQueryTransformer（查询改写）
      → HybridDocumentRetriever（向量 + BM25 + RRF 融合）
      → ContextualQueryAugmenter（上下文注入）
    → MessageChatMemoryAdvisor（对话摘要压缩）
    → LLM API 调用（+ MCP 工具按需挂载）
  → CircuitBreaker 熔断保护
  → 输出护栏（敏感信息脱敏）
  → 流式内容 + 引用来源 → 前端
```

看起来链路很长，但 Spring AI 的 Advisor 机制让这些逻辑的组织很自然，每一层都是可插拔的。

## 后续计划

正如开头所说，这是系列文章的起点。后面我会按维度逐一展开，每篇聊一个主题：

- **Spring AI Advisor 机制**：这套插件化的调用链设计，是理解整个项目架构的关键
- **Embedding 与向量检索**：从自写适配器到拥抱框架原生方案的演进
- **混合检索的实现**：向量 + BM25 + RRF，为什么这么做，效果怎么样
- **分块策略的迭代**：简单分割 → 递归分割 + 重叠窗口，以及参数调优的思考
- **对话记忆**：从窗口截断到摘要压缩，怎么在上下文完整性和 token 成本之间取舍
- **AI 护栏与弹性保护**：输入拦截、输出脱敏、限流熔断重试的实战落地
- **MCP 工具调用**：两个项目之间怎么通过 MCP 协议实现工具互通

每篇都会结合代码和实际场景来讲，不会只停留在概念层面。如果你也是 Java 开发、也想了解怎么把 AI 能力落地到实际项目中，希望这个系列能给你一些参考。

---

*项目完整代码：[github.com/prove0603/business-qa](https://github.com/prove0603/business-qa)*
