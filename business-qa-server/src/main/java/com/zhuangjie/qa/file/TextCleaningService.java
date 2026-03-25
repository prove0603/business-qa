package com.zhuangjie.qa.file;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 文本清洗服务，去除 Tika 解析产物中的噪声内容。
 *
 * Tika 从 PDF/Word 中提取文本时，会带出一些无用内容：
 * - 嵌入的图片文件名（如 image001.png）
 * - 图片 URL 和本地文件路径
 * - 控制字符（\u0000-\u001F）
 * - 连续的分隔线（---、===）
 * - 多余的空行和行尾空白
 *
 * 清洗后的文本存入 t_document.content，供后续分块和向量化使用。
 */
@Service
public class TextCleaningService {

    /** 匹配 Tika 解析 PDF/Word 时产生的图片文件名行（如 "image1.png"） */
    private static final Pattern IMAGE_FILENAME_LINE =
            Pattern.compile("(?m)^image\\d+\\.(png|jpe?g|gif|bmp|webp)\\s*$");

    /** 匹配 HTTP 图片 URL */
    private static final Pattern IMAGE_URL =
            Pattern.compile("https?://\\S+?\\.(png|jpe?g|gif|bmp|webp)(\\?\\S*)?", Pattern.CASE_INSENSITIVE);

    /** 匹配 file:// 本地路径 */
    private static final Pattern FILE_URL =
            Pattern.compile("file:(//)?\\S+", Pattern.CASE_INSENSITIVE);

    /** 匹配 Markdown/文档中的分隔线（---、===、*** 等） */
    private static final Pattern SEPARATOR_LINE =
            Pattern.compile("(?m)^\\s*[-_*=]{3,}\\s*$");

    /** 匹配不可见控制字符（保留换行 \n 和制表符 \t） */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");

    /**
     * 清洗文本：去除噪声内容，规范化空白字符。
     * 处理顺序：控制字符 → 图片噪声 → 路径噪声 → 分隔线 → 换行符规范化 → 行尾空白 → 多余空行
     */
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

        // 统一换行符为 \n，清除行尾空白，压缩连续空行为最多两个
        t = t.replace("\r\n", "\n").replace("\r", "\n");
        t = t.replaceAll("(?m)[ \t]+$", "");
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.strip();
    }
}
