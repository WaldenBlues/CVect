package com.walden.cvect.model.fact.extract;

import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.ChunkType;

public class EducationExtractor implements FactExtractor {
    @Override
    public boolean supports(ResumeChunk chunk) {
        return chunk.getType() == ChunkType.EDUCATION;
    }

    @Override
    public String extract(ResumeChunk chunk) {
        String text = chunk.getContent();
        // 这里的逻辑可以更复杂：提取学校、专业、学历
        // 目前先做简单的清洗
        return text.replaceAll("(?i)^(教育背景|教育经历|Education)[:：]?", "").trim();
    }

    @Override
    public ExtractorMode mode() {
        return ExtractorMode.EXCLUSIVE; // 整个教育块通常作为一个整体存入
    }
}