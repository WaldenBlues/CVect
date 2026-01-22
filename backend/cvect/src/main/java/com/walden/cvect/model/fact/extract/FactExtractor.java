package com.walden.cvect.model.fact.extract;

import com.walden.cvect.model.ResumeChunk;

public interface FactExtractor {
    /**
     * 处理原始 Chunk，返回提取/清洗后的内容
     */
    String extract(ResumeChunk chunk);

    /**
     * 该提取器支持的类型
     */
    boolean supports(ResumeChunk chunk);

    ExtractorMode mode();

}