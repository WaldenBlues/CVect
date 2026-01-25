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

    /**
     * 两阶段提取：先处理所有 ADDITIVE，再处理一个 EXCLUSIVE
     */
    public List<String> extractAll(ResumeChunk chunk) {
        List<String> results = new ArrayList<>();

        extractors.stream()
                .filter(e -> e.mode() == ExtractorMode.ADDITIVE)
                .filter(e -> e.supports(chunk))
                .map(e -> e.extract(chunk))
                .filter(s -> !s.isBlank())
                .forEach(results::add);

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
