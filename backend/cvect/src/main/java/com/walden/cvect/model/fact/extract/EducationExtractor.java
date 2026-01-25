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
        return text.replaceAll("(?i)^(教育背景|教育经历|Education)[:：]?", "").trim();
    }

    @Override
    public ExtractorMode mode() {
        return ExtractorMode.EXCLUSIVE;
    }
}