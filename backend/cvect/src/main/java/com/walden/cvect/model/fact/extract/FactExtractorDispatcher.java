package com.walden.cvect.model.fact.extract;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.walden.cvect.model.ResumeChunk;

@Component
public class FactExtractorDispatcher {

    private final List<FactExtractor> extractors;

    public FactExtractorDispatcher(List<FactExtractor> extractors) {
        this.extractors = extractors;
    }

    public List<String> extractAll(ResumeChunk chunk) {
        List<String> results = new ArrayList<>();

        // 1️⃣ 先跑所有 ADDITIVE Extractor
        extractors.stream()
                .filter(e -> e.mode() == ExtractorMode.ADDITIVE)
                .filter(e -> e.supports(chunk))
                .map(e -> e.extract(chunk))
                .filter(s -> !s.isBlank())
                .forEach(results::add);

        // 2️⃣ 再跑一个 EXCLUSIVE Extractor（最多一个）
        extractors.stream()
                .filter(e -> e.mode() == ExtractorMode.EXCLUSIVE)
                .filter(e -> e.supports(chunk))
                .findFirst()
                .map(e -> e.extract(chunk))
                .filter(s -> !s.isBlank())
                .ifPresent(results::add);

        return results;
    }

}
