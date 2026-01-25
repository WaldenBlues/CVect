package com.walden.cvect.service;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ResumeChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultChunkerService implements ChunkerService {

    private static final int MIN_CHUNK_LENGTH = 5;

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

        if (text.length() > 80 && !containsColon(text)) {
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
        if (text.endsWith("教育"))
            return ChunkType.EDUCATION;
        if (text.endsWith("项目") || text.endsWith("经历"))
            return ChunkType.EXPERIENCE;
        if (text.endsWith("技能"))
            return ChunkType.SKILL;
        if (text.endsWith("荣誉"))
            return ChunkType.HONOR;
        return ChunkType.OTHER;
    }

    private boolean containsColon(String text) {
        return text.contains("：") || text.contains(":");
    }

    private boolean isSectionTitle(String text) {
        return text.length() <= 6
                && (text.endsWith("经历")
                        || text.endsWith("项目")
                        || text.endsWith("技能")
                        || text.endsWith("教育")
                        || text.endsWith("荣誉"));
    }

    private boolean isHeaderLike(String text) {
        String lower = text.toLowerCase();

        if (text.length() > 30)
            return false;

        if (lower.contains("熟练")
                || lower.contains("掌握")
                || lower.contains("使用")
                || lower.contains("技术")
                || lower.contains("skill")) {
            return false;
        }

        return lower.contains("工程师")
                || lower.contains("developer")
                || lower.contains("software")
                || lower.contains("简历")
                || lower.matches("^[\\u4e00-\\u9fa5]{2,4}\\s+[a-zA-Z].*");
    }

    private boolean isContactSignal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String lower = text.toLowerCase();

        if (lower.contains("@")
                || lower.contains("电话")
                || lower.contains("手机")
                || lower.contains("邮箱")
                || lower.contains("email")
                || lower.contains("联系方式")
                || lower.contains("wechat")
                || lower.contains("微信")) {
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
        return (lower.contains("教育")
                || lower.contains("education")
                || lower.contains("大学")
                || lower.contains("学院"))
                && (lower.matches(".*\\d{4}.*")
                        || lower.contains("学士")
                        || lower.contains("硕士")
                        || lower.contains("博士"));
    }

    private boolean isExperienceLike(String lower) {
        return lower.contains("项目")
                || lower.contains("经历")
                || lower.contains("实习")
                || lower.contains("experience")
                || lower.contains("负责");
    }

    private boolean isSkillLike(String lower) {
        return lower.contains("技能")
                || lower.contains("技术")
                || lower.contains("skill")
                || lower.contains("熟练")
                || lower.contains("掌握");
    }

    private boolean isHonorLike(String lower) {
        return lower.contains("奖")
                || lower.contains("竞赛")
                || lower.contains("证书")
                || lower.contains("荣誉")
                || lower.contains("rank");
    }

    private boolean isLinkLike(String lower) {
        return lower.contains("github.com")
                || lower.contains("gitee.com")
                || lower.contains("blog")
                || lower.contains("个人博客");
    }
}
