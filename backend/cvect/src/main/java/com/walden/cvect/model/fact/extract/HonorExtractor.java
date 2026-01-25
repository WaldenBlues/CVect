package com.walden.cvect.model.fact.extract;

import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.ChunkType;

public class HonorExtractor implements FactExtractor {

    @Override
    public boolean supports(ResumeChunk chunk) {
        return chunk.getType() == ChunkType.HONOR;
    }

    @Override
    public String extract(ResumeChunk chunk) {
        String text = chunk.getContent();
        if (text == null || text.isBlank()) {
            return "";
        }

        // 去除常见 section 前缀
        text = text.replaceAll("(?i)^(荣誉奖励|获奖情况|个人荣誉)[:：]?", "");

        // 压缩空白
        text = text.replaceAll("\\s+", " ").trim();

        // 过短则丢弃
        if (text.length() < 4) {
            return "";
        }

        return text;
    }

    @Override
    public ExtractorMode mode() {
        return ExtractorMode.EXCLUSIVE;
    }
}
