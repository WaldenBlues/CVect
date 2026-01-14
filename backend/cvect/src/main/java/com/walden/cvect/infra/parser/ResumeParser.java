package com.walden.cvect.infra.parser;

import java.io.InputStream;

public interface ResumeParser {

    /**
     * 将简历文件解析为纯文本
     *
     * @param inputStream 文件输入流
     * @param contentType MIME 类型（如 application/pdf）
     * @return 解析后的纯文本
     */
    String parse(InputStream inputStream, String contentType);
}
