package com.walden.cvect.service;

import com.walden.cvect.model.ResumeChunk;

import java.util.List;

public interface ChunkerService {

    /**
     * 将规范化后的简历文本切分为语义 chunk
     */
    List<ResumeChunk> chunk(String normalizedText);
}
