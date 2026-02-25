package com.walden.cvect.model.fact.extract;

import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.Regex;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LinkExtractor implements FactExtractor {

    private static final Pattern LEADING_GITHUB_LABEL = Pattern.compile(
            "(?i)github\\s*https?://(?:www\\.)?github\\.com/");
    private static final Pattern LEADING_GITEE_LABEL = Pattern.compile(
            "(?i)gitee\\s*https?://(?:www\\.)?gitee\\.com/");
    private static final Pattern GITHUB_SUFFIX = Pattern.compile(
            "(?i)^(https?://(?:www\\.)?github\\.com/)([A-Za-z0-9-]+?)github([/?#].*)?$");
    private static final Pattern GITEE_SUFFIX = Pattern.compile(
            "(?i)^(https?://(?:www\\.)?gitee\\.com/)([A-Za-z0-9-]+?)gitee([/?#].*)?$");

    @Override
    public boolean supports(ResumeChunk chunk) {
        return chunk.getType() == ChunkType.LINK;
    }

    @Override
    public String extract(ResumeChunk chunk) {
        String text = chunk.getContent();
        Matcher matcher = Regex.URL_STRICT.matcher(text);
        if (matcher.find()) {
            return sanitizeUrl(matcher.group(), text);
        }
        return "";
    }

    private String sanitizeUrl(String url, String rawText) {
        String result = trimTrailingPunctuation(url);
        result = trimPlatformLabelSuffix(result, rawText);
        return result;
    }

    private String trimTrailingPunctuation(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int end = url.length();
        while (end > 0) {
            char ch = url.charAt(end - 1);
            if (isTrailingNoise(ch)) {
                end--;
                continue;
            }
            break;
        }
        return url.substring(0, end);
    }

    private boolean isTrailingNoise(char ch) {
        return ch == '.' || ch == ',' || ch == ';' || ch == ':' || ch == ')' || ch == ']'
                || ch == '}' || ch == '>' || ch == '"' || ch == '\'' || ch == '，'
                || ch == '。' || ch == '；' || ch == '：' || ch == '）'
                || ch == '】' || ch == '》' || ch == '”' || ch == '’';
    }

    private String trimPlatformLabelSuffix(String url, String rawText) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (rawText == null || rawText.isBlank()) {
            return url;
        }

        if (LEADING_GITHUB_LABEL.matcher(rawText).find()) {
            Matcher matcher = GITHUB_SUFFIX.matcher(url);
            if (matcher.matches()) {
                return matcher.group(1) + matcher.group(2) + (matcher.group(3) == null ? "" : matcher.group(3));
            }
        }
        if (LEADING_GITEE_LABEL.matcher(rawText).find()) {
            Matcher matcher = GITEE_SUFFIX.matcher(url);
            if (matcher.matches()) {
                return matcher.group(1) + matcher.group(2) + (matcher.group(3) == null ? "" : matcher.group(3));
            }
        }
        return url;
    }

    @Override
    public ExtractorMode mode() {
        return ExtractorMode.ADDITIVE;
    }
}
