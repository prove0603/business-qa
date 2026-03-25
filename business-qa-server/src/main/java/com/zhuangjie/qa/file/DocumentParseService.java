package com.zhuangjie.qa.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 通用文档解析服务，使用 Apache Tika 从各种格式文件中提取纯文本。
 *
 * 支持的格式：PDF、DOCX、DOC、TXT、Markdown、RTF 等。
 * Tika 的 AutoDetectParser 会自动识别文件格式并选择对应的解析器。
 *
 * 解析后的文本会经过 TextCleaningService 清洗，去除图片链接、控制字符等噪声。
 * 清洗后的文本存入 t_document.content 字段，后续用于分块和向量化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseService {

    /** 单个文件最大可提取的文本长度（5MB），防止 OOM */
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;

    private final TextCleaningService textCleaningService;

    /** 从上传文件中提取文本内容 */
    public String parseContent(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("开始解析文件: {}", fileName);

        if (file.isEmpty() || file.getSize() == 0) {
            return "";
        }

        try (InputStream inputStream = file.getInputStream()) {
            String content = doParse(inputStream);
            String cleaned = textCleaningService.cleanText(content);
            log.info("文件解析完成，文本长度: {} 字符", cleaned.length());
            return cleaned;
        } catch (IOException | TikaException | SAXException e) {
            log.error("文件解析失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件解析失败: " + e.getMessage());
        }
    }

    /** 从字节数组中提取文本（供非 MultipartFile 场景使用） */
    public String parseContent(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            return "";
        }

        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            String content = doParse(inputStream);
            return textCleaningService.cleanText(content);
        } catch (IOException | TikaException | SAXException e) {
            log.error("文件解析失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * Tika 解析核心逻辑。
     *
     * - AutoDetectParser: 自动检测文件格式，选择对应解析器（PDF/Word/TXT 等）
     * - BodyContentHandler: 只提取正文文本，忽略元数据
     * - NoOpEmbeddedDocumentExtractor: 跳过嵌入的图片/附件，避免解析噪声
     * - PDFParserConfig: PDF 特殊配置——不提取内嵌图片，按位置排序文本
     */
    private String doParse(InputStream inputStream) throws IOException, TikaException, SAXException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
        Metadata metadata = new Metadata();

        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        // 禁用嵌入文档解析，避免提取出图片二进制内容
        context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());

        // PDF 专用配置：不解析内嵌图片，按物理位置排序文本（保持阅读顺序）
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setSortByPosition(true);
        context.set(PDFParserConfig.class, pdfConfig);

        parser.parse(inputStream, handler, metadata, context);
        return handler.toString();
    }

    /** 空操作的嵌入文档提取器，跳过文档中嵌入的图片、附件等 */
    private static class NoOpEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) {
        }
    }
}
