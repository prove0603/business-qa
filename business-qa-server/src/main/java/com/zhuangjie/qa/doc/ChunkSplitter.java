package com.zhuangjie.qa.doc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 递归分块器：按语义边界递归分割文档，并在相邻块之间保留重叠区域。
 * <p>
 * 分割优先级：Markdown 标题 → 空行（段落） → 句号/换行 → 硬截断。
 * 每一级分割后，如果子片段仍然超过 maxChunkSize，则递归使用下一级分割符继续拆分。
 * 分块之间保留 overlap 个字符的重叠，使检索时上下文不会在边界处丢失。
 */
@Slf4j
@Component
public class ChunkSplitter {

    private static final List<Pattern> SPLIT_PATTERNS = List.of(
            Pattern.compile("(?<=\n)(?=#{1,3}\\s)"),
            Pattern.compile("\n{2,}"),
            Pattern.compile("(?<=[。！？.!?])\n?"),
            Pattern.compile("\n")
    );

    private final int maxChunkSize;
    private final int minChunkSize;
    private final int overlap;

    public ChunkSplitter(
            @Value("${qa.rag.chunk-size:800}") int maxChunkSize,
            @Value("${qa.rag.chunk-min-size:80}") int minChunkSize,
            @Value("${qa.rag.chunk-overlap:150}") int overlap) {
        this.maxChunkSize = maxChunkSize;
        this.minChunkSize = minChunkSize;
        this.overlap = overlap;
    }

    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> rawChunks = recursiveSplit(content.strip(), 0);
        List<String> result = applyOverlap(rawChunks);

        if (result.isEmpty() && content.length() >= minChunkSize) {
            result.add(content.strip());
        }

        log.debug("Split document into {} chunks (maxSize={}, overlap={})", result.size(), maxChunkSize, overlap);
        return result;
    }

    /**
     * 递归分割：尝试当前级别的分割符；子片段仍超长则用下一级继续拆。
     */
    private List<String> recursiveSplit(String text, int level) {
        if (text.length() <= maxChunkSize) {
            return text.length() >= minChunkSize ? List.of(text) : List.of();
        }

        if (level >= SPLIT_PATTERNS.size()) {
            return hardSplit(text);
        }

        String[] parts = SPLIT_PATTERNS.get(level).split(text);
        if (parts.length <= 1) {
            return recursiveSplit(text, level + 1);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String part : parts) {
            if (buffer.length() + part.length() > maxChunkSize && !buffer.isEmpty()) {
                chunks.add(buffer.toString().strip());
                buffer = new StringBuilder();
            }
            buffer.append(part);
        }

        if (!buffer.isEmpty()) {
            String remaining = buffer.toString().strip();
            if (!remaining.isEmpty()) {
                chunks.add(remaining);
            }
        }

        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() > maxChunkSize) {
                result.addAll(recursiveSplit(chunk, level + 1));
            } else if (chunk.length() >= minChunkSize) {
                result.add(chunk);
            } else if (!result.isEmpty()) {
                result.set(result.size() - 1, result.getLast() + "\n" + chunk);
            }
        }

        return result;
    }

    private List<String> hardSplit(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxChunkSize) {
            String sub = text.substring(i, Math.min(i + maxChunkSize, text.length())).strip();
            if (sub.length() >= minChunkSize) {
                chunks.add(sub);
            } else if (!chunks.isEmpty()) {
                chunks.set(chunks.size() - 1, chunks.getLast() + sub);
            }
        }
        return chunks;
    }

    /**
     * 在相邻块之间插入重叠：每个块的开头包含上一个块末尾的 overlap 个字符。
     */
    private List<String> applyOverlap(List<String> chunks) {
        if (chunks.size() <= 1 || overlap <= 0) {
            return chunks;
        }

        List<String> result = new ArrayList<>();
        result.add(chunks.getFirst());

        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String overlapText = prev.substring(Math.max(0, prev.length() - overlap));
            result.add(overlapText + "\n…\n" + chunks.get(i));
        }
        return result;
    }
}
