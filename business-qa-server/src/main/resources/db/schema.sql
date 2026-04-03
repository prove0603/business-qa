-- Business QA Database Schema
-- Database: business_qa
-- Requires: PostgreSQL 16+ with pgvector extension

CREATE EXTENSION IF NOT EXISTS vector;

-- Module configuration
CREATE TABLE IF NOT EXISTS t_module (
    id BIGSERIAL PRIMARY KEY,
    module_name VARCHAR(100) NOT NULL,
    module_code VARCHAR(100) NOT NULL UNIQUE,
    git_remote_url VARCHAR(500),
    git_branch VARCHAR(100) DEFAULT 'master',
    module_type VARCHAR(20) NOT NULL,
    description TEXT,
    last_sync_commit VARCHAR(64),
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_module IS 'Business system module configuration';
COMMENT ON COLUMN t_module.module_type IS 'COMMON = auto-included in queries, TASK = user-selectable';

-- Business documents
CREATE TABLE IF NOT EXISTS t_document (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    file_type VARCHAR(20) DEFAULT 'MARKDOWN',
    content_hash VARCHAR(64),
    original_filename VARCHAR(300),
    content_type VARCHAR(100),
    file_key VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    chunk_count INT DEFAULT 0,
    vectorized BOOLEAN DEFAULT FALSE,
    version INT DEFAULT 1,
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_module_id ON t_document(module_id);

-- Change detection records
CREATE TABLE IF NOT EXISTS t_change_detection (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL,
    from_commit VARCHAR(64),
    to_commit VARCHAR(64),
    changed_files TEXT,
    changed_file_count INT DEFAULT 0,
    suggestion_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_detection_module_id ON t_change_detection(module_id);

-- AI document modification suggestions
CREATE TABLE IF NOT EXISTS t_change_suggestion (
    id BIGSERIAL PRIMARY KEY,
    detection_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    affected_section VARCHAR(500),
    original_text TEXT,
    suggested_text TEXT,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_suggestion_detection_id ON t_change_suggestion(detection_id);
CREATE INDEX idx_change_suggestion_status ON t_change_suggestion(status);

-- Chat sessions
CREATE TABLE IF NOT EXISTS t_chat_session (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    module_filter TEXT,
    message_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat messages
CREATE TABLE IF NOT EXISTS t_chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    source_refs TEXT,
    tokens_used INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_message_session_id ON t_chat_message(session_id);

-- Prompt templates (configurable system/user prompts)
CREATE TABLE IF NOT EXISTS t_prompt_template (
    id BIGSERIAL PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL,
    template_key VARCHAR(100) NOT NULL UNIQUE,
    content TEXT NOT NULL,
    template_type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_prompt_template IS 'Prompt template management for AI interactions';
COMMENT ON COLUMN t_prompt_template.template_key IS 'Unique key: CHAT_SYSTEM, ANALYSIS_SYSTEM, ANALYSIS_USER, etc.';
COMMENT ON COLUMN t_prompt_template.template_type IS 'SYSTEM = system prompt, USER = user prompt template';

-- Default prompt templates
INSERT INTO t_prompt_template (template_name, template_key, content, template_type, description) VALUES
('对话系统提示词', 'CHAT_SYSTEM', '你是一个智能业务助手，具备以下能力：
1. 基于业务文档的知识问答（RAG 自动检索相关文档）
2. 查询SQL性能分析结果（当用户问到SQL风险、SQL问题、SQL性能等话题时，使用可用的工具查询真实数据）

回答规则：
- 优先参考 RAG 检索到的文档内容作答
- 如果文档中没有相关信息，且有可用的工具，请使用工具查询
- 使用与用户提问相同的语言回答
- 【引用规则】当你引用了某份文档的内容时，请在引用处标注来源，格式：[来源：《文档标题》]
- 如果多个文档都与问题相关，请综合引用并分别标注来源
- 回答结尾，用一行列出所有引用过的文档标题', 'SYSTEM', '主对话的系统提示词，控制AI的角色和行为'),
('代码分析系统提示词', 'ANALYSIS_SYSTEM', 'You are a technical analyst. Analyze code changes and suggest document updates.', 'SYSTEM', '代码变更分析的系统提示词'),
('代码分析用户提示词', 'ANALYSIS_USER', 'You are a technical analyst. Analyze the code changes and existing documents.

## Code Changes (Diff)
```
%s
```

## Existing Documents for Module: %s
%s

## Task
Analyze if any existing documents need updates based on the code changes.
Return a JSON array of suggestions. Each suggestion:
```json
[
  {
    "document_id": <number>,
    "affected_section": "<section title or description>",
    "original_text": "<relevant original text snippet>",
    "suggested_text": "<suggested updated text>",
    "reason": "<why this change is needed>"
  }
]
```
If no updates are needed, return an empty array: []
Return ONLY the JSON array, no other text.', 'USER', '代码变更分析的用户提示词模板，支持 %s 占位符：1=diff内容 2=模块名 3=文档上下文')
ON CONFLICT (template_key) DO NOTHING;

-- Guardrail rules
CREATE TABLE IF NOT EXISTS t_guardrail_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    pattern VARCHAR(1000) NOT NULL,
    action VARCHAR(20) NOT NULL DEFAULT 'BLOCK',
    reply_message VARCHAR(500),
    description VARCHAR(500),
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_guardrail_rule IS 'AI guardrail rules for input/output safety';
COMMENT ON COLUMN t_guardrail_rule.rule_type IS 'INPUT_KEYWORD = input keyword block, INPUT_REGEX = input regex, OUTPUT_REGEX = output regex mask';
COMMENT ON COLUMN t_guardrail_rule.action IS 'BLOCK = reject input, WARN = allow with warning, MASK = mask output content';

-- Default guardrail rules
INSERT INTO t_guardrail_rule (rule_name, rule_type, pattern, action, reply_message, description, sort_order) VALUES
('Prompt注入防护-忽略指令', 'INPUT_REGEX', '(?i)(ignore|忽略|忘记|forget).{0,20}(previous|之前|上面|above|instruction|指令|提示)', 'BLOCK', '检测到潜在的提示词注入攻击，请重新描述您的问题。', '防止用户通过指令注入覆盖系统提示词', 1),
('Prompt注入防护-角色扮演', 'INPUT_REGEX', '(?i)(你现在是|you are now|act as|pretend|假装|扮演).{0,30}(DAN|evil|恶意|hack)', 'BLOCK', '检测到潜在的提示词注入攻击，请重新描述您的问题。', '防止角色扮演型提示注入', 2),
('Prompt注入防护-系统提示泄露', 'INPUT_REGEX', '(?i)(system prompt|系统提示|系统消息|reveal|泄露|显示).{0,20}(prompt|提示词|指令|instruction)', 'BLOCK', '抱歉，无法提供系统提示词内容。请问有什么业务问题需要帮助？', '防止用户尝试获取系统提示词', 3),
('输出脱敏-手机号', 'OUTPUT_REGEX', '1[3-9]\d{9}', 'MASK', NULL, '自动遮蔽11位手机号码', 10),
('输出脱敏-身份证号', 'OUTPUT_REGEX', '\d{17}[\dXx]', 'MASK', NULL, '自动遮蔽18位身份证号', 11),
('输出脱敏-银行卡号', 'OUTPUT_REGEX', '\d{16,19}', 'MASK', NULL, '自动遮蔽16-19位银行卡号', 12)
ON CONFLICT DO NOTHING;

-- Vector table (qa_vector) is auto-created by Spring AI PgVectorStore
-- After the table is created, run the following to enable full-text search for hybrid retrieval:
CREATE INDEX IF NOT EXISTS idx_qa_vector_content_fts ON qa_vector USING GIN(to_tsvector('simple', content));
