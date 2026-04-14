package com.zhuangjie.qa.change;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhuangjie.qa.db.entity.ChangeDetection;
import com.zhuangjie.qa.db.entity.ChangeSuggestion;
import com.zhuangjie.qa.db.entity.PromptTemplate;
import com.zhuangjie.qa.db.entity.QaDocument;
import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ChangeSuggestionDbService;
import com.zhuangjie.qa.db.service.DocumentDbService;
import com.zhuangjie.qa.db.service.PromptTemplateDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 文档更新建议生成器：基于代码变更 diff 和现有文档，调用 LLM 分析并生成文档更新建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionGenerator {

    @Qualifier("analysisChatClient")
    private final ChatClient analysisChatClient;
    private final DocumentDbService documentDbService;
    private final ChangeSuggestionDbService changeSuggestionDbService;
    private final PromptTemplateDbService promptTemplateDbService;

    private static final String DEFAULT_ANALYSIS_PROMPT = """
            You are a technical analyst. Analyze the code changes and existing documents.
            
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
            Return ONLY the JSON array, no other text.
            """;

    /**
     * 异步执行：加载模块下文档上下文与提示词，调用 {@code analysisChatClient} 分析 diff 与文档后生成建议并入库。
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

            // 控制 prompt 长度，避免超出模型上下文
            String truncatedDiff = diffContent.length() > 8000
                    ? diffContent.substring(0, 8000) + "\n... (truncated)"
                    : diffContent;

            String promptTemplate = loadAnalysisPrompt();
            String prompt = promptTemplate.formatted(truncatedDiff, module.getModuleName(), docsContext);

            // 调用分析专用 ChatClient，期望返回可解析的 JSON 数组
            String response = analysisChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            parseSuggestionsAndSave(detection.getId(), response);
        } catch (Exception e) {
            log.error("Failed to generate suggestions for detection {}: {}", detection.getId(), e.getMessage(), e);
        }
    }

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
     * 从数据库加载分析提示词模板（键 {@code ANALYSIS_USER}）；加载失败或无记录时使用硬编码默认模板。
     */
    private String loadAnalysisPrompt() {
        try {
            PromptTemplate template = promptTemplateDbService.getByKey("ANALYSIS_USER");
            if (template != null) {
                return template.getContent();
            }
        } catch (Exception e) {
            log.warn("Failed to load ANALYSIS_USER prompt from DB: {}", e.getMessage());
        }
        return DEFAULT_ANALYSIS_PROMPT;
    }

    /**
     * 解析 AI 返回的 JSON 数组格式建议，逐条构造 {@link ChangeSuggestion} 并写入数据库。
     */
    private void parseSuggestionsAndSave(Long detectionId, String aiResponse) {
        try {
            // 去掉模型可能包裹的 markdown 代码块标记
            String cleaned = aiResponse.trim();
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
