package com.zhuangjie.qa.chat;

import com.zhuangjie.qa.db.entity.QaModule;
import com.zhuangjie.qa.db.service.ModuleDbService;
import com.zhuangjie.qa.rag.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorService vectorService;
    private final ModuleDbService moduleDbService;

    /**
     * Stream chat with RAG - retrieves relevant documents then streams LLM response.
     *
     * @param question  user question
     * @param moduleIds user-selected module IDs (COMMON modules auto-included)
     * @param sessionId chat session ID for memory
     * @return streaming response
     */
    public Flux<String> streamChat(String question, List<Long> moduleIds, Long sessionId) {
        List<Long> effectiveModuleIds = buildEffectiveModuleIds(moduleIds);

        List<Document> docs = vectorService.search(question, effectiveModuleIds, 5);

        String context = docs.isEmpty()
                ? "（无相关文档）"
                : docs.stream()
                        .map(d -> "【%s】\n%s".formatted(d.getMetadata().getOrDefault("doc_title", "Unknown"), d.getText()))
                        .collect(Collectors.joining("\n\n---\n\n"));

        String augmentedQuestion = """
                基于以下参考文档回答用户的问题。
                如果文档中没有相关信息，请明确说明。
                在回答中引用文档标题。
                
                ## 参考文档
                %s
                
                ## 用户问题
                %s
                """.formatted(context, question);

        String conversationId = sessionId != null ? sessionId.toString() : "default";

        return chatClient.prompt()
                .user(augmentedQuestion)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    /**
     * Builds effective module ID list: user-selected + all COMMON modules.
     */
    private List<Long> buildEffectiveModuleIds(List<Long> userSelectedIds) {
        List<QaModule> commonModules = moduleDbService.listByType("COMMON");
        List<Long> commonIds = commonModules.stream().map(QaModule::getId).toList();

        List<Long> effective = new ArrayList<>(commonIds);
        if (userSelectedIds != null) {
            for (Long id : userSelectedIds) {
                if (!effective.contains(id)) {
                    effective.add(id);
                }
            }
        }
        return effective;
    }

    /**
     * Extracts source references from retrieved documents.
     */
    public List<SourceReference> extractSourceRefs(String question, List<Long> moduleIds) {
        List<Long> effectiveModuleIds = buildEffectiveModuleIds(moduleIds);
        List<Document> docs = vectorService.search(question, effectiveModuleIds, 5);

        return docs.stream()
                .map(d -> new SourceReference(
                        (String) d.getMetadata().getOrDefault("doc_title", "Unknown"),
                        (String) d.getMetadata().getOrDefault("module_code", ""),
                        d.getText().length() > 200 ? d.getText().substring(0, 200) + "..." : d.getText()
                ))
                .distinct()
                .toList();
    }

    public record SourceReference(String docTitle, String moduleCode, String excerpt) {}
}
