package com.zhuangjie.qa.doc;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits document content into chunks for vector embedding.
 * Strategy: split by Markdown headings first, then by paragraph, with a max chunk size fallback.
 */
@Component
public class ChunkSplitter {

    private static final int MAX_CHUNK_SIZE = 1000;
    private static final int MIN_CHUNK_SIZE = 50;
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,3}\\s+", Pattern.MULTILINE);

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

        if (chunks.isEmpty() && content.length() >= MIN_CHUNK_SIZE) {
            chunks.add(content.trim());
        }

        return chunks;
    }

    private List<String> splitByHeadings(String content) {
        String[] parts = HEADING_PATTERN.split(content);
        List<String> sections = new ArrayList<>();
        var matcher = HEADING_PATTERN.matcher(content);

        int idx = 0;
        for (String part : parts) {
            if (!part.isBlank()) {
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

    private List<String> splitBySize(String text, int maxSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > maxSize && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }

        if (!current.isEmpty()) {
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.length() >= MIN_CHUNK_SIZE || chunks.isEmpty()) {
                    chunks.add(remaining);
                } else if (!chunks.isEmpty()) {
                    chunks.set(chunks.size() - 1, chunks.getLast() + "\n\n" + remaining);
                }
            }
        }

        return chunks;
    }
}
