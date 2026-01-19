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

        // 1. 统一换行符
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // 2. 去掉连续空行（>=3 → 2）
        text = text.replaceAll("\n{3,}", "\n\n");

        // 3. 链接过滤与噪声清理
        // 逻辑：删除所有 https? 链接，但排除掉包含 github.com 或 gitee.com 的链接
        // [^\\s\\n]+ 匹配直到遇到空格或换行，确保能抓取完整的 URL
        text = text.replaceAll("https?://(?!github\\.com|gitee\\.com)[^\\s\\n]+", "");

        // 去掉页码噪声 (如 1/3, 2/5)
        text = text.replaceAll("(?m)^\\s*\\d+\\s*/\\s*\\d+\\s*$", "");

        // 4. 首尾 trim
        return text.trim();
    }
}