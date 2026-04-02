-- Insert default prompt templates
INSERT INTO t_prompt_template (template_name, template_key, content, template_type, description) VALUES
('对话系统提示词', 'CHAT_SYSTEM', '你是一个智能业务助手，具备以下能力：
1. 基于业务文档的知识问答（RAG 自动检索相关文档）
2. 查询SQL性能分析结果（当用户问到SQL风险、SQL问题、SQL性能等话题时，使用可用的工具查询真实数据）

回答规则：
- 优先参考 RAG 检索到的文档内容作答
- 如果文档中没有相关信息，且有可用的工具，请使用工具查询
- 在回答中引用文档标题作为来源
- 使用与用户提问相同的语言回答', 'SYSTEM', '主对话的系统提示词，控制AI的角色和行为'),
('代码分析系统提示词', 'ANALYSIS_SYSTEM', 'You are a technical analyst. Analyze code changes and suggest document updates.', 'SYSTEM', '代码变更分析的系统提示词')
ON CONFLICT (template_key) DO NOTHING;

-- Insert default guardrail rules
INSERT INTO t_guardrail_rule (rule_name, rule_type, pattern, action, reply_message, description, sort_order) VALUES
('Prompt注入防护-忽略指令', 'INPUT_REGEX', '(?i)(ignore|忽略|忘记|forget).{0,20}(previous|之前|上面|above|instruction|指令|提示)', 'BLOCK', '检测到潜在的提示词注入攻击，请重新描述您的问题。', '防止用户通过指令注入覆盖系统提示词', 1),
('Prompt注入防护-角色扮演', 'INPUT_REGEX', '(?i)(你现在是|you are now|act as|pretend|假装|扮演).{0,30}(DAN|evil|恶意|hack)', 'BLOCK', '检测到潜在的提示词注入攻击，请重新描述您的问题。', '防止角色扮演型提示注入', 2),
('Prompt注入防护-系统提示泄露', 'INPUT_REGEX', '(?i)(system prompt|系统提示|系统消息|reveal|泄露|显示).{0,20}(prompt|提示词|指令|instruction)', 'BLOCK', '抱歉，无法提供系统提示词内容。请问有什么业务问题需要帮助？', '防止用户尝试获取系统提示词', 3),
('输出脱敏-手机号', 'OUTPUT_REGEX', '1[3-9]\d{9}', 'MASK', NULL, '自动遮蔽11位手机号码', 10),
('输出脱敏-身份证号', 'OUTPUT_REGEX', '\d{17}[\dXx]', 'MASK', NULL, '自动遮蔽18位身份证号', 11)
ON CONFLICT DO NOTHING;
