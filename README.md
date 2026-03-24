# Business QA — 业务智能问答助手

基于 RAG（检索增强生成）的业务系统智能问答助手。上传业务文档后，AI 可基于文档内容回答自然语言问题，并在代码变更时自动给出文档修改建议。

## 功能概览

- **智能问答（RAG）**：自然语言提问，AI 基于文档内容生成回答，附带引用来源
- **文档管理**：支持在线编辑和文件上传（PDF/Word/TXT/Markdown），自动分块向量化
- **模块组织**：按业务模块归类文档，COMMON 模块问答时自动包含，TASK 模块按需勾选
- **变更检测**：Git 代码变更检测 + AI 生成文档修改建议
- **流式对话**：SSE 实时流式输出，Markdown 渲染 + 代码高亮
- **文件存储**：MinIO（S3 兼容）存储上传文件，Apache Tika 解析文档内容

## 功能完成状态

| 功能 | 状态 | 说明 |
|------|------|------|
| 模块管理（CRUD） | ✅ 已完成 | 支持 COMMON/TASK 类型，关联 Git 仓库 |
| 文档管理（在线编辑 + 文件上传） | ✅ 已完成 | 支持 PDF/Word/TXT/Markdown，MinIO 存储 |
| 文档分块 + 向量化 | ✅ 已完成 | Markdown 标题分块，异步向量化，PgVectorStore |
| RAG 智能问答 | ✅ 已完成 | 模块过滤检索 + SSE 流式 + 多轮对话记忆 |
| 引用来源展示 | ✅ 已完成 | AI 回答附带引用的文档标题和模块 |
| 仪表盘 | ✅ 已完成 | 模块数/文档数/会话数/待处理建议数 |
| Git 变更检测 | ✅ 已完成 | JGit 拉取 + diff 检测 + AI 分析建议 |
| 变更建议管理 | ⚠️ 部分完成 | 可查看/忽略建议，"应用建议"仅更新状态，未自动修改文档 |

### 下一阶段规划

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 应用建议自动修改文档 | 高 | 点击"应用"后将 AI 建议的修改内容写入文档正文，触发重新向量化 |
| 文档版本历史 | 高 | 记录文档每次修改的版本快照，支持回滚 |
| Markdown 编辑器 | 中 | 文档在线编辑替换为富文本 Markdown 编辑器（如 Milkdown/Tiptap） |
| 仪表盘增强 | 中 | 按模块统计、文档向量化趋势图、问答热度排行 |
| 向量化失败重试 | 中 | 向量化失败时记录状态，支持手动/自动重试 |
| 用户认证 | 低 | 接入登录认证，支持多用户隔离 |
| 导出问答记录 | 低 | 支持导出对话历史为 PDF/Markdown |

## 技术栈

| 层面 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.4, Spring AI 1.0.0 |
| AI 模型 | DashScope（百炼）qwen-plus，OpenAI 兼容接口 |
| Embedding | DashScope text-embedding-v3（1024 维） |
| 向量库 | PostgreSQL + pgvector（HNSW 索引，余弦距离） |
| ORM | MyBatis-Plus 3.5.7 |
| 文件存储 | MinIO（S3 兼容）+ Apache Tika 文档解析 |
| Git 操作 | JGit 7.1.0 |
| 前端 | Vue 3, TypeScript, Element Plus, Vite 6 |

## 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- PostgreSQL 16+（需安装 pgvector 扩展）
- Maven 3.9+
- MinIO（可选，用于文件上传存储）

### 数据库初始化

```bash
createdb business_qa
psql business_qa -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql business_qa -f business-qa-server/src/main/resources/db/schema.sql
```

### 后端启动

```bash
# 复制配置文件并填入实际值
cp business-qa-server/src/main/resources/application-dev.yml.example \
   business-qa-server/src/main/resources/application-dev.yml
# 编辑 application-dev.yml，填入数据库密码、API Key 等

# 构建并运行
mvn clean package -DskipTests
java -jar business-qa-server/target/business-qa-server-1.0.0-SNAPSHOT.jar
```

后端服务地址：http://localhost:8091

### 前端启动

```bash
cd business-qa-ui
npm install
npm run dev
```

开发服务地址：http://localhost:5174

### MinIO 启动（可选）

```powershell
# Windows PowerShell
.\scripts\start-minio.ps1

# 或 CMD
scripts\start-minio.bat
```

MinIO 控制台：http://localhost:9001

## 项目结构

```
business-qa/
├── pom.xml                          # 父 POM
├── scripts/                         # MinIO 启动脚本
├── business-qa-server/              # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/zhuangjie/qa/
│       ├── config/                  # Spring 配置（AI、S3、MyBatis 等）
│       ├── controller/              # REST 接口
│       ├── chat/                    # 对话引擎（RAG + SSE 流式）
│       ├── doc/                     # 文档管理 + 分块 + 向量化
│       ├── file/                    # 文件基础设施（S3 存储、Tika 解析）
│       ├── change/                  # Git 变更检测 + AI 建议
│       ├── rag/                     # Embedding + VectorStore
│       └── db/                      # 实体、Mapper、DbService
├── business-qa-ui/                  # Vue 3 前端
│   └── src/
│       ├── views/                   # 页面
│       ├── components/              # 组件
│       └── api/                     # API 层
└── .cursor/rules/                   # Cursor AI 规则
```

## 配置说明

需要配置的环境变量 / 配置项：

| 变量 | 说明 |
|------|------|
| `POSTGRES_PASSWORD` | PostgreSQL 密码 |
| `AI_DASHSCOPE_API_KEY` | DashScope（百炼）API Key |
| `GIT_USERNAME` | Git 仓库认证用户名 |
| `GIT_PASSWORD` | Git 仓库密码/Token |
| `QA_STORAGE_ENDPOINT` | MinIO 地址（默认 http://localhost:9000） |
| `QA_STORAGE_ACCESS_KEY` | MinIO 访问密钥（默认 minioadmin） |
| `QA_STORAGE_SECRET_KEY` | MinIO 密钥（默认 minioadmin） |

> 敏感配置写在 `application-dev.yml` 中（已被 .gitignore 排除），参考 `application-dev.yml.example`。

## License

MIT
