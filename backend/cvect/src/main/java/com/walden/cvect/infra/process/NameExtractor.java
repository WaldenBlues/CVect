package com.walden.cvect.infra.process;

import com.walden.cvect.model.fact.Regex;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从规范化文本中提取姓名
 */
@Component
public class NameExtractor {

    private static final Pattern NAME_LABEL_PATTERN =
            Pattern.compile("^\\s*(姓名|name)\\s*[:：]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_INLINE_PATTERN =
            Pattern.compile("^\\s*(.*?)\\s*([\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,})\\s*$");
    private static final Pattern PHONE_INLINE_PATTERN =
            Pattern.compile("^\\s*(.*?)\\s*(\\+?86?\\s*1[3-9]\\d{9})\\s*$");
    private static final Pattern BILINGUAL_NAME_PATTERN =
            Pattern.compile("([\\u4e00-\\u9fa5]{2,5})\\s+[A-Za-z][A-Za-z\\-']+(\\s+[A-Za-z][A-Za-z\\-']+)?");
    private static final String[] NAME_BLOCK_KEYWORDS = new String[]{
            "简历", "resume", "项目", "经历", "教育", "技能", "荣誉", "奖", "赛", "证书", "博客",
            "工程师", "开发", "负责人", "求职", "实习", "杯", "个人", "联系方式", "电子邮件", "邮箱", "电话", "手机"
    };
    private static final String[] EMAIL_KEYWORDS = new String[]{
            "邮箱", "电子邮件", "email", "e-mail"
    };
    private static final String[] NAME_BLOCK_KEYWORDS_LOWER = toLowerKeywords(NAME_BLOCK_KEYWORDS);

    public String extract(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return "";
        }

        String[] lines = normalizedText.split("\\n");

        // 规则1：匹配 “姓名/Name: xxx”
        for (String line : lines) {
            String name = extractByLabel(line);
            if (!name.isBlank()) {
                return name;
            }
        }

        // 规则2：找包含“邮箱/邮件/email”等关键词的行直接提取姓名
        for (String line : lines) {
            if (containsEmailKeyword(line)) {
                String cleaned = stripContactKeywords(line);
                if (cleaned.isBlank() || containsBlockKeyword(cleaned)) {
                    continue;
                }
                String name = sanitizeName(cleaned);
                if (!name.isBlank()) {
                    return name;
                }
            }
        }

        // 规则3：找包含邮箱/电话的行，优先截取邮箱/电话前的姓名
        for (String line : lines) {
            String name = extractBeforeContact(line);
            if (!name.isBlank()) {
                return name;
            }
        }

        // 规则4：找邮箱所在行的上一行或下一行（很多简历是“姓名 + 邮箱/电话”）
        for (int i = 0; i < lines.length; i++) {
            if (Regex.EMAIL_STRICT.matcher(lines[i]).find()) {
                String name = extractNeighborName(lines, i);
                if (!name.isBlank()) {
                    return name;
                }
            }
        }

        // 规则5：取首行短文本作为姓名（避免正文误判）
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > 20) {
                continue;
            }
            if (Regex.EMAIL_STRICT.matcher(trimmed).find() || Regex.PHONE_STRICT.matcher(trimmed).find()) {
                continue;
            }
            if (containsBlockKeyword(trimmed)) {
                continue;
            }
            String name = sanitizeName(trimmed);
            if (!name.isBlank()) {
                return name;
            }
            break;
        }

        return "";
    }

    private String extractByLabel(String line) {
        Matcher matcher = NAME_LABEL_PATTERN.matcher(line);
        if (!matcher.find()) {
            return "";
        }
        String raw = matcher.group(2);
        return sanitizeName(raw);
    }

    private String extractBeforeContact(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }

        Matcher emailMatcher = EMAIL_INLINE_PATTERN.matcher(line);
        if (emailMatcher.find()) {
            return sanitizeName(emailMatcher.group(1));
        }

        Matcher phoneMatcher = PHONE_INLINE_PATTERN.matcher(line);
        if (phoneMatcher.find()) {
            return sanitizeName(phoneMatcher.group(1));
        }

        return "";
    }

    private String extractFromContactLine(String line) {
        if (line == null) {
            return "";
        }

        // 去掉邮箱/电话等噪声字段
        String cleaned = Regex.EMAIL_STRICT.matcher(line).replaceAll(" ");
        cleaned = Regex.PHONE_STRICT.matcher(cleaned).replaceAll(" ");

        cleaned = stripContactKeywords(cleaned);

        return sanitizeName(cleaned);
    }

    private String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.replaceAll("[\\s\\|,/，·•]+", " ").trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        Matcher bilingual = BILINGUAL_NAME_PATTERN.matcher(trimmed);
        if (bilingual.find()) {
            String name = bilingual.group(1);
            return isLikelyName(name, raw) ? name : "";
        }

        // 如果包含中文，优先提取连续中文姓名
        Matcher cn = Pattern.compile("([\\u4e00-\\u9fa5]{2,5})").matcher(trimmed);
        if (cn.find()) {
            String name = cn.group(1);
            return isLikelyName(name, raw) ? name : "";
        }

        // 英文姓名：取前两个词
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return "";
        }
        if (parts.length == 1) {
            String name = parts[0];
            return isLikelyName(name, raw) ? name : "";
        }
        String name = (parts[0] + " " + parts[1]).trim();
        return isLikelyName(name, raw) ? name : "";
    }

    private boolean isLikelyName(String name, String raw) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (containsBlockKeyword(name) || containsBlockKeyword(raw)) {
            return false;
        }
        if (name.matches("^[\\u4e00-\\u9fa5]{2,5}$")) {
            return true;
        }
        return name.matches("^[A-Za-z]+(\\s+[A-Za-z]+)?$");
    }

    private String extractNeighborName(String[] lines, int emailIndex) {
        String next = findNextNonBlank(lines, emailIndex + 1);
        String name = extractFromContactLine(next);
        if (!name.isBlank()) {
            return name;
        }
        String prev = findPrevNonBlank(lines, emailIndex - 1);
        return extractFromContactLine(prev);
    }

    private String findNextNonBlank(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                return lines[i];
            }
        }
        return "";
    }

    private String findPrevNonBlank(String[] lines, int start) {
        for (int i = start; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                return lines[i];
            }
        }
        return "";
    }

    private boolean containsEmailKeyword(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String keyword : EMAIL_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String stripContactKeywords(String line) {
        if (line == null) {
            return "";
        }
        String cleaned = line;
        cleaned = Regex.EMAIL_STRICT.matcher(cleaned).replaceAll(" ");
        cleaned = Regex.PHONE_STRICT.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replace("电话", " ")
                .replace("手机", " ")
                .replace("邮箱", " ")
                .replace("电子邮件", " ")
                .replace("Email", " ")
                .replace("email", " ")
                .replace("E-mail", " ")
                .replace("联系方式", " ");
        return cleaned.trim();
    }

    private boolean containsBlockKeyword(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String keyword : NAME_BLOCK_KEYWORDS_LOWER) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String[] toLowerKeywords(String[] keywords) {
        String[] lowered = new String[keywords.length];
        for (int i = 0; i < keywords.length; i++) {
            lowered[i] = keywords[i].toLowerCase(Locale.ROOT);
        }
        return lowered;
    }
}
