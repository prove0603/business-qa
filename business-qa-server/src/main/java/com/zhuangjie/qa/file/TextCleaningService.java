package com.zhuangjie.qa.file;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 文本清理服务：去除解析产物中的噪声内容
 */
@Service
public class TextCleaningService {

    private static final Pattern IMAGE_FILENAME_LINE =
            Pattern.compile("(?m)^image\\d+\\.(png|jpe?g|gif|bmp|webp)\\s*$");

    private static final Pattern IMAGE_URL =
            Pattern.compile("https?://\\S+?\\.(png|jpe?g|gif|bmp|webp)(\\?\\S*)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern FILE_URL =
            Pattern.compile("file:(//)?\\S+", Pattern.CASE_INSENSITIVE);

    private static final Pattern SEPARATOR_LINE =
            Pattern.compile("(?m)^\\s*[-_*=]{3,}\\s*$");

    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");

    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String t = text;
        t = CONTROL_CHARS.matcher(t).replaceAll("");
        t = IMAGE_FILENAME_LINE.matcher(t).replaceAll("");
        t = IMAGE_URL.matcher(t).replaceAll("");
        t = FILE_URL.matcher(t).replaceAll("");
        t = SEPARATOR_LINE.matcher(t).replaceAll("");

        t = t.replace("\r\n", "\n").replace("\r", "\n");
        t = t.replaceAll("(?m)[ \t]+$", "");
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.strip();
    }
}
