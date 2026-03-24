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

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionGenerator {

    @Qualifier("analysisChatClient")
    private final ChatClient analysisChatClient;
    private final DocumentDbService documentDbService;
    private final ChangeSuggestionDbService changeSuggestionDbService;

    private static final String ANALYSIS_PROMPT = """
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

    @Async
    public void generateSuggestions(ChangeDetection detection, QaModule module, String diffContent) {
        try {
            List<QaDocument> docs = documentDbService.listByModuleId(module.getId());
            if (docs.isEmpty()) {
                log.info("No documents found for module {}, skipping suggestion generation", module.getModuleCode());
                return;
            }

            String docsContext = buildDocsContext(docs);

            String truncatedDiff = diffContent.length() > 8000
                    ? diffContent.substring(0, 8000) + "\n... (truncated)"
                    : diffContent;

            String prompt = ANALYSIS_PROMPT.formatted(truncatedDiff, module.getModuleName(), docsContext);

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

    private void parseSuggestionsAndSave(Long detectionId, String aiResponse) {
        try {
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
