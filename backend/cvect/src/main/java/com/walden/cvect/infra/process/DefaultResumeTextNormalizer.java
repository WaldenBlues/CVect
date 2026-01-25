package com.walden.cvect.infra.process;

import org.springframework.stereotype.Component;

@Component
public class DefaultResumeTextNormalizer implements ResumeTextNormalizer {

    @Override
    public String normalize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String text = rawText;

        // 统一换行符
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // 压缩空行
        text = text.replaceAll("\n{3,}", "\n\n");

        // 过滤非代码仓库链接
        text = text.replaceAll("https?://(?!github\\.com|gitee\\.com)[^\\s\\n]+", "");

        // 去掉页码噪声
        text = text.replaceAll("(?m)^\\s*\\d+\\s*/\\s*\\d+\\s*$", "");

        return text.trim();
    }
}