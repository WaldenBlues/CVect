package com.walden.cvect.infra.parser;

import com.walden.cvect.exception.ResumeParseException;
import com.walden.cvect.model.ParseResult;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TikaResumeParser implements ResumeParser {

    private static final int MAX_CHARS = 1 * 1024 * 1024; // 1MB 限制
    private final Parser parser = new AutoDetectParser();

    @Override
    public ParseResult parse(InputStream inputStream, String contentType) {
        Metadata metadata = new Metadata();
        if (contentType != null) {
            metadata.set(TikaCoreProperties.CONTENT_TYPE_HINT, contentType);
        }

        boolean truncated = false;
        WriteOutContentHandler handler = new WriteOutContentHandler(MAX_CHARS);

        try (TemporaryResources tmp = new TemporaryResources();
                TikaInputStream tis = TikaInputStream.get(inputStream, tmp, metadata)) {

            ParseContext context = createParseContext();

            try {
                parser.parse(tis, handler, metadata, context);
            } catch (SAXException e) {
                // 如果是超过字数限制，标记为截断，不抛异常
                if (WriteLimitReachedException.isWriteLimitReached(e)) {
                    truncated = true;
                } else {
                    throw new ResumeParseException("文档结构解析异常 (SAX)", e);
                }
            } catch (TikaException e) {
                throw new ResumeParseException("Tika 提取内容失败", e);
            }

            String content = handler.toString();
            String detectedType = metadata.get(Metadata.CONTENT_TYPE);

            return new ParseResult(
                    content,
                    truncated,
                    detectedType,
                    content.length());

        } catch (IOException e) {
            throw new ResumeParseException("文件流处理或解析 IO 失败", e);
        }
    }

    private ParseContext createParseContext() {
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        // 策略 A: 禁用内嵌文档处理器
        context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());

        // 策略 B: PDF 专用配置
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);

        context.set(PDFParserConfig.class, pdfConfig);
        return context;
    }

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