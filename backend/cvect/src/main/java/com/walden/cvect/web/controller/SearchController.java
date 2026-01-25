package com.walden.cvect.web.controller;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 候选人搜索 API - 基于向量相似度
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final VectorStoreService vectorStore;
    private final EmbeddingService embeddingService;

    public SearchController(VectorStoreService vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * 根据职位描述搜索匹配的候选人
     *
     * @param request 搜索请求
     * @return 按相似度排序的候选人列表
     */
    @PostMapping
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        // 生成职位描述的 embedding
        float[] queryEmbedding = embeddingService.embed(request.jobDescription());

        // 向量搜索
        ChunkType[] types = request.filterByExperience() && request.filterBySkill()
                ? new ChunkType[]{ChunkType.EXPERIENCE, ChunkType.SKILL}
                : request.filterByExperience()
                    ? new ChunkType[]{ChunkType.EXPERIENCE}
                    : request.filterBySkill()
                        ? new ChunkType[]{ChunkType.SKILL}
                        : null;

        List<VectorStoreService.SearchResult> results =
                vectorStore.search(queryEmbedding, request.topK(), types);

        // 按候选人聚合结果
        Map<UUID, CandidateMatch> candidateMatches = new LinkedHashMap<>();
        for (VectorStoreService.SearchResult r : results) {
            candidateMatches.computeIfAbsent(r.candidateId(), cid -> new CandidateMatch(
                    cid,
                    new ArrayList<>(),
                    0.0f
            )).matchedChunks().add(new MatchedChunk(
                    r.chunkType().name(),
                    r.content(),
                    r.score()
            ));
        }

        // 计算综合相似度 (取最高分)并排序
        List<CandidateMatch> sortedCandidates = candidateMatches.entrySet().stream()
                .map(entry -> {
                    UUID candidateId = entry.getKey();
                    CandidateMatch match = entry.getValue();
                    float maxScore = match.matchedChunks().stream()
                            .map(MatchedChunk::score)
                            .max(Float::compareTo)
                            .orElse(0.0f);
                    return new CandidateMatch(candidateId, match.matchedChunks(), maxScore);
                })
                .sorted(Comparator.comparing(CandidateMatch::score).reversed())
                .toList();

        return ResponseEntity.ok(new SearchResponse(
                sortedCandidates.size(),
                request.topK(),
                sortedCandidates
        ));
    }

    /**
     * 创建 HNSW 索引 (管理接口)
     */
    @PostMapping("/admin/create-index")
    public ResponseEntity<String> createIndex() {
        vectorStore.createHnswIndex();
        return ResponseEntity.ok("HNSW index created successfully");
    }

    // 请求/响应 DTOs
    public record SearchRequest(
            @NotBlank String jobDescription,
            int topK,
            boolean filterByExperience,
            boolean filterBySkill
    ) {
        public SearchRequest {
            if (topK <= 0) topK = 10;
            if (topK > 100) topK = 100;
        }
    }

    public record SearchResponse(
            int totalResults,
            int requested,
            List<CandidateMatch> candidates
    ) {}

    public record CandidateMatch(
            UUID candidateId,
            List<MatchedChunk> matchedChunks,
            float score
    ) {}

    public record MatchedChunk(
            String chunkType,
            String content,
            float score
    ) {}
}
