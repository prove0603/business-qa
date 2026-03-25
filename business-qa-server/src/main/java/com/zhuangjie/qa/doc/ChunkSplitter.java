package com.zhuangjie.qa.doc;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档分块器，将长文本切分为适合 embedding 的小片段。
 *
 * 分块质量直接影响 RAG 检索效果：
 * - chunk 太大：包含太多无关信息，检索精度低，且消耗更多 embedding token
 * - chunk 太小：缺失上下文，LLM 基于碎片化信息难以给出好答案
 *
 * 当前分块策略（两级）：
 * 1. 第一级：按 Markdown 标题（# / ## / ###）切分，每个标题段落作为一个 section
 * 2. 第二级：section 超过 MAX_CHUNK_SIZE 时，按段落（\n\n）进一步切分
 *
 * 已知不足（优化方向）：
 * - 无重叠（Overlapping）：相邻 chunk 之间可能丢失边界信息
 * - 固定大小阈值：不同类型文档（代码 vs 文档）可能需要不同策略
 * - 未保留标题层级路径：chunk 丢失了"它在文档中的位置"信息
 */
@Component
public class ChunkSplitter {

    /** 单个 chunk 最大字符数 */
    private static final int MAX_CHUNK_SIZE = 1000;
    /** 低于此长度的片段会被丢弃（避免产生无意义的碎片） */
    private static final int MIN_CHUNK_SIZE = 50;
    /** 匹配 Markdown 一到三级标题（# / ## / ###） */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,3}\\s+", Pattern.MULTILINE);

    /**
     * 主入口：将文档内容分割为 chunk 列表。
     *
     * 流程：
     * 1. splitByHeadings() — 按标题切分为若干 section
     * 2. 对每个 section：
     *    - 长度 ≤ MAX_CHUNK_SIZE 且 ≥ MIN_CHUNK_SIZE → 直接作为一个 chunk
     *    - 长度 > MAX_CHUNK_SIZE → splitBySize() 进一步按段落切分
     * 3. 如果最终 chunks 为空但原文有内容 → 将原文整体作为一个 chunk
     */
    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> sections = splitByHeadings(content);
        List<String> chunks = new ArrayList<>();

        for (String section : sections) {
            if (section.length() <= MAX_CHUNK_SIZE) {
                if (section.length() >= MIN_CHUNK_SIZE) {
                    chunks.add(section.trim());
                }
            } else {
                chunks.addAll(splitBySize(section, MAX_CHUNK_SIZE));
            }
        }

        // 兜底：如果分块结果为空但原文有足够内容，保留整篇文档作为单一 chunk
        if (chunks.isEmpty() && content.length() >= MIN_CHUNK_SIZE) {
            chunks.add(content.trim());
        }

        return chunks;
    }

    /**
     * 按 Markdown 标题切分文档。
     * 使用正则 split 拆分，同时通过 matcher 恢复标题前缀，
     * 保证每个 section 以标题行开头。
     */
    private List<String> splitByHeadings(String content) {
        String[] parts = HEADING_PATTERN.split(content);
        List<String> sections = new ArrayList<>();
        var matcher = HEADING_PATTERN.matcher(content);

        int idx = 0;
        for (String part : parts) {
            if (!part.isBlank()) {
                // 第一个 part 前面没有标题（可能是文档的开头段落）
                String heading = "";
                if (idx > 0) {
                    matcher.find();
                    heading = matcher.group();
                }
                sections.add((heading + part).trim());
            }
            idx++;
        }

        if (sections.isEmpty()) {
            sections.add(content);
        }
        return sections;
    }

    /**
     * 按段落和大小限制切分过长的 section。
     * 以 \n\n（空行）作为段落分隔符，贪心合并段落直到接近 maxSize，
     * 超出则开始新的 chunk。
     *
     * 尾部碎片处理：如果最后剩余片段太短（< MIN_CHUNK_SIZE），
     * 则追加到上一个 chunk 而非单独成块。
     */
    private List<String> splitBySize(String text, int maxSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            // 当前累积 + 新段落超出限制，先把累积的输出为一个 chunk
            if (current.length() + paragraph.length() > maxSize && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }

        // 处理最后剩余的内容
        if (!current.isEmpty()) {
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.length() >= MIN_CHUNK_SIZE || chunks.isEmpty()) {
                    chunks.add(remaining);
                } else if (!chunks.isEmpty()) {
                    // 碎片太短，合并到上一个 chunk
                    chunks.set(chunks.size() - 1, chunks.getLast() + "\n\n" + remaining);
                }
            }
        }

        return chunks;
    }
}
