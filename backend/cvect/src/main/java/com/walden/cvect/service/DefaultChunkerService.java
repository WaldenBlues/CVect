package com.walden.cvect.service;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ResumeChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultChunkerService implements ChunkerService {

    private static final int MIN_CHUNK_LENGTH = 5;
    private static final int MAX_SECTION_TITLE_LENGTH = 6;
    private static final int MAX_HEADER_LENGTH = 30;
    private static final int LONG_TEXT_THRESHOLD = 80;

    private static final List<String> SECTION_TITLE_SUFFIXES = List.of(
            "经历", "项目", "技能", "教育", "荣誉");
    private static final List<String> HEADER_NEGATIVE_KEYWORDS = List.of(
            "熟练", "掌握", "使用", "技术", "skill");
    private static final List<String> HEADER_POSITIVE_KEYWORDS = List.of(
            "工程师", "developer", "software", "简历");
    private static final List<String> CONTACT_KEYWORDS = List.of(
            "@", "电话", "手机", "邮箱", "email", "联系方式", "wechat", "微信");
    private static final List<String> EDUCATION_KEYWORDS = List.of(
            "教育", "education", "大学", "学院");
    private static final List<String> EXPERIENCE_KEYWORDS = List.of(
            "项目", "经历", "实习", "experience", "负责");
    private static final List<String> SKILL_KEYWORDS = List.of(
            "技能", "技术", "skill", "熟练", "掌握");
    private static final List<String> HONOR_KEYWORDS = List.of(
            "奖", "竞赛", "证书", "荣誉", "rank");
    private static final List<String> LINK_KEYWORDS = List.of(
            "github.com", "gitee.com", "blog", "个人博客");

    @Override
    public List<ResumeChunk> chunk(String normalizedText) {
        List<ResumeChunk> result = new ArrayList<>();
        if (normalizedText == null || normalizedText.isBlank()) {
            return result;
        }

        String[] blocks = normalizedText.split("\n\n");

        StringBuilder buffer = new StringBuilder();
        ChunkType currentType = ChunkType.OTHER;

        for (String block : blocks) {
            String content = block.trim();
            if (content.isEmpty()) {
                continue;
            }

            // 章节标题触发状态切换
            if (isSectionTitle(content)) {
                ChunkType titleType = mapSectionTitle(content);
                if (titleType != ChunkType.OTHER) {
                    flushChunk(result, buffer, currentType);
                    buffer.setLength(0);
                    currentType = titleType;
                }
                continue;
            }

            ChunkType inferredType = inferType(content);

            if (shouldStartNewChunk(inferredType, currentType)) {
                flushChunk(result, buffer, currentType);
                buffer.setLength(0);
                currentType = inferredType;
            }

            buffer.append(content).append("\n\n");
        }

        flushChunk(result, buffer, currentType);
        return result;
    }

    /**
     * 状态机切换规则
     */
    private boolean shouldStartNewChunk(ChunkType inferred, ChunkType current) {
        if (inferred == ChunkType.OTHER) {
            return false;
        }

        // HEADER 永远不吞非 HEADER
        if (current == ChunkType.HEADER && inferred != ChunkType.HEADER) {
            return true;
        }

        // 强语义块：永远独立
        if (isStrongIndependentChunk(inferred)) {
            return true;
        }

        // CONTACT 允许聚合，但禁止跨区
        if (current == ChunkType.CONTACT && inferred != ChunkType.CONTACT) {
            return true;
        }

        return inferred != current;
    }

    private boolean isStrongIndependentChunk(ChunkType type) {
        return type == ChunkType.CONTACT
                || type == ChunkType.LINK
                || type == ChunkType.HONOR;
    }

    /**
     * 输出 chunk，过滤过短内容
     */
    private void flushChunk(List<ResumeChunk> result,
            StringBuilder buffer,
            ChunkType type) {

        if (buffer.length() == 0) {
            return;
        }

        boolean isStructural = type == ChunkType.CONTACT
                || type == ChunkType.LINK
                || type == ChunkType.HEADER;

        if (!isStructural && buffer.length() < MIN_CHUNK_LENGTH) {
            return;
        }

        result.add(new ResumeChunk(
                result.size(),
                buffer.toString().trim(),
                type));
    }

    /**
     * 段首语义信号推断
     */
    private ChunkType inferType(String text) {
        String lower = text.toLowerCase();

        if (text.length() > LONG_TEXT_THRESHOLD && !containsColon(text)) {
            return ChunkType.OTHER;
        }

        if (isContactSignal(text)) {
            return ChunkType.CONTACT;
        }

        if (isHeaderLike(text)) {
            return ChunkType.HEADER;
        }

        if (isEducationLike(lower)) {
            return ChunkType.EDUCATION;
        }

        if (isExperienceLike(lower)) {
            return ChunkType.EXPERIENCE;
        }

        if (isSkillLike(lower)) {
            return ChunkType.SKILL;
        }

        if (isHonorLike(lower)) {
            return ChunkType.HONOR;
        }

        if (isLinkLike(lower)) {
            return ChunkType.LINK;
        }

        return ChunkType.OTHER;
    }

    private ChunkType mapSectionTitle(String text) {
        if (text.endsWith("教育")) {
            return ChunkType.EDUCATION;
        }
        if (text.endsWith("项目") || text.endsWith("经历")) {
            return ChunkType.EXPERIENCE;
        }
        if (text.endsWith("技能")) {
            return ChunkType.SKILL;
        }
        if (text.endsWith("荣誉")) {
            return ChunkType.HONOR;
        }
        return ChunkType.OTHER;
    }

    private boolean containsColon(String text) {
        return text.contains("：") || text.contains(":");
    }

    private boolean isSectionTitle(String text) {
        return text.length() <= MAX_SECTION_TITLE_LENGTH
                && endsWithAny(text, SECTION_TITLE_SUFFIXES);
    }

    private boolean isHeaderLike(String text) {
        String lower = text.toLowerCase();

        if (text.length() > MAX_HEADER_LENGTH) {
            return false;
        }

        if (containsAny(lower, HEADER_NEGATIVE_KEYWORDS)) {
            return false;
        }

        return containsAny(lower, HEADER_POSITIVE_KEYWORDS)
                || lower.matches("^[\\u4e00-\\u9fa5]{2,4}\\s+[a-zA-Z].*");
    }

    private boolean isContactSignal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String lower = text.toLowerCase();

        if (containsAny(lower, CONTACT_KEYWORDS)) {
            return true;
        }

        if (lower.matches(".*(19|20)\\d{2}[.\\-/]\\d{1,2}.*")) {
            return false;
        }

        String digits = lower.replaceAll("[^0-9]", "");

        if (digits.length() == 11 && digits.startsWith("1")) {
            return true;
        }

        if (digits.length() == 13 && digits.startsWith("86")) {
            return true;
        }

        return false;
    }

    private boolean isEducationLike(String lower) {
        return containsAny(lower, EDUCATION_KEYWORDS)
                && (lower.matches(".*\\d{4}.*")
                || lower.contains("学士")
                || lower.contains("硕士")
                || lower.contains("博士"));
    }

    private boolean isExperienceLike(String lower) {
        return containsAny(lower, EXPERIENCE_KEYWORDS);
    }

    private boolean isSkillLike(String lower) {
        return containsAny(lower, SKILL_KEYWORDS);
    }

    private boolean isHonorLike(String lower) {
        return containsAny(lower, HONOR_KEYWORDS);
    }

    private boolean isLinkLike(String lower) {
        return containsAny(lower, LINK_KEYWORDS);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean endsWithAny(String text, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (text.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
