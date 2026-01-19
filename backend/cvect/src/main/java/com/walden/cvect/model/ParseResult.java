package com.walden.cvect.model;

public final class ParseResult {

    private final String content; // 解析出的纯文本
    private final boolean truncated; // 是否因 MAX_CHARS 被截断
    private final String contentType; // Tika 最终识别的类型
    private final int charCount; // 实际字符数

    public ParseResult(
            String content,
            boolean truncated,
            String contentType,
            int charCount) {
        this.content = content;
        this.truncated = truncated;
        this.contentType = contentType;
        this.charCount = charCount;
    }

    public String getContent() {
        return content;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getContentType() {
        return contentType;
    }

    public int getCharCount() {
        return charCount;
    }
}
