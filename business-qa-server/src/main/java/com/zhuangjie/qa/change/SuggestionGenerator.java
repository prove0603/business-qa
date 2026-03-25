package com.zhuangjie.qa.change;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhuangjie.qa.db.entity.ChangeDetection;
import com.zhuangjie.qa.db.entity.ChangeSuggestion;
import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ChangeSuggestionDbService;
import com.zhuangjie.qa.db.service.DocumentDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 建议生成器，分析代码变更并提出文档更新建议。
 *
 * 这是项目中 LLM 的第二种使用模式（区别于 RAG 问答）：
 * - 不使用向量检索，而是直接将 diff 内容 + 现有文档作为上下文
 * - 使用独立的 analysisChatClient（无对话记忆）
 * - 同步调用 .call()（非流式），因为需要完整的 JSON 响应
 * - 通过 Prompt 约束 AI 返回结构化 JSON（手动解析）
 *
 * 优化方向：可改用 Spring AI 的 Structured Output（entity() 方法）自动解析 JSON。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionGenerator {

    /** 代码分析专用 ChatClient，不带对话记忆 */
    @Qualifier("analysisChatClient")
    private final ChatClient analysisChatClient;
    private final DocumentDbService documentDbService;
    private final ChangeSuggestionDbService changeSuggestionDbService;

    /**
     * 分析 Prompt 模板。
     * 包含三部分上下文：代码 diff、模块名、现有文档内容。
     * 要求 AI 返回纯 JSON 数组，每条建议包含受影响的文档 ID、修改区域、建议文本和原因。
     */
    private static final String ANALYSIS_PROMPT = """
            你是一个技术分析师。请分析以下代码变更和现有文档。
            
            ## 代码变更（Diff）
            ```
            %s
            ```
            
            ## 模块「%s」的现有文档
            %s
            
            ## 任务
            分析代码变更是否影响现有文档，判断哪些文档需要更新。
            返回一个 JSON 数组，每条建议包含以下字段：
            ```json
            [
              {
                "document_id": <文档ID>,
                "affected_section": "<受影响的章节标题或描述>",
                "original_text": "<相关的原始文本片段>",
                "suggested_text": "<建议修改后的文本>",
                "reason": "<需要修改的原因>"
              }
            ]
            ```
            如果不需要更新，返回空数组：[]
            只返回 JSON 数组，不要包含其他文本。
            """;

    /**
     * 异步生成文档更新建议。
     *
     * 流程：
     * 1. 加载模块下所有文档作为 AI 上下文
     * 2. 截断过长的 diff（防止超过 LLM token 限制）
     * 3. 拼接 Prompt 并调用 analysisChatClient（同步调用，非流式）
     * 4. 解析 AI 返回的 JSON，逐条保存到 t_change_suggestion 表
     */
    @Async
    public void generateSuggestions(ChangeDetection detection, QaModule module, String diffContent) {
        try {
            List<QaDocument> docs = documentDbService.listByModuleId(module.getId());
            if (docs.isEmpty()) {
                log.info("No documents found for module {}, skipping suggestion generation", module.getModuleCode());
                return;
            }

            String docsContext = buildDocsContext(docs);

            // 截断 diff 到 8000 字符，避免总 prompt 过长超出模型 token 限制
            String truncatedDiff = diffContent.length() > 8000
                    ? diffContent.substring(0, 8000) + "\n... (truncated)"
                    : diffContent;

            String prompt = ANALYSIS_PROMPT.formatted(truncatedDiff, module.getModuleName(), docsContext);

            // 同步调用 LLM：.call().content() 返回完整文本（非流式）
            String response = analysisChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            parseSuggestionsAndSave(detection.getId(), response);
        } catch (Exception e) {
            log.error("Failed to generate suggestions for detection {}: {}", detection.getId(), e.getMessage(), e);
        }
    }

    /** 将文档列表格式化为 Prompt 上下文（每篇文档截取前 2000 字符） */
    private String buildDocsContext(List<QaDocument> docs) {
        StringBuilder sb = new StringBuilder();
        for (QaDocument doc : docs) {
            sb.append("### Document ID: ").append(doc.getId())
                    .append(" - ").append(doc.getTitle()).append("\n");
            String preview = doc.getContent().length() > 2000
                    ? doc.getContent().substring(0, 2000) + "..."
                    : doc.getContent();
            sb.append(preview).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 解析 AI 返回的 JSON 并保存为建议记录。
     *
     * AI 可能在 JSON 外包裹 markdown 代码块（```json...```），需要先清理。
     * 解析失败（AI 返回非法 JSON）只打 warn 日志，不抛异常。
     */
    private void parseSuggestionsAndSave(Long detectionId, String aiResponse) {
        try {
            String cleaned = aiResponse.trim();
            // 有些 LLM 会在 JSON 外包裹 ```json ... ``` 标记
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "");
            }

            JSONArray arr = JSONUtil.parseArray(cleaned);
            int count = 0;

            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ChangeSuggestion suggestion = new ChangeSuggestion();
                suggestion.setDetectionId(detectionId);
                suggestion.setDocumentId(obj.getLong("document_id"));
                suggestion.setAffectedSection(obj.getStr("affected_section"));
                suggestion.setOriginalText(obj.getStr("original_text"));
                suggestion.setSuggestedText(obj.getStr("suggested_text"));
                suggestion.setReason(obj.getStr("reason"));
                suggestion.setStatus("PENDING");
                changeSuggestionDbService.save(suggestion);
                count++;
            }

            log.info("Generated {} suggestions for detection {}", count, detectionId);
        } catch (Exception e) {
            log.warn("Failed to parse AI suggestions: {}", e.getMessage());
        }
    }
}
