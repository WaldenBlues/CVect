package com.walden.cvect.web.controller;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
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
    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 1000;
    private static final int CHUNK_OVERSAMPLE_FACTOR = 4;
    private static final int MAX_CHUNK_TOP_K = 4000;

    private final VectorStoreService vectorStore;
    private final EmbeddingService embeddingService;
    private final VectorIngestTaskJpaRepository vectorIngestTaskRepository;

    public SearchController(
            VectorStoreService vectorStore,
            EmbeddingService embeddingService,
            VectorIngestTaskJpaRepository vectorIngestTaskRepository) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.vectorIngestTaskRepository = vectorIngestTaskRepository;
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
        ChunkType[] types = resolveChunkTypes(request);
        WeightConfig weightConfig = resolveWeights(request, types);

        int chunkTopK = resolveChunkTopK(request.topK());
        List<VectorStoreService.SearchResult> results =
                vectorStore.search(queryEmbedding, chunkTopK, types);

        // 按候选人聚合并排序
        List<CandidateMatch> sortedCandidates = aggregateAndSort(results, weightConfig);
        if (request.onlyVectorReadyCandidates()) {
            sortedCandidates = sortedCandidates.stream()
                    .filter(this::isVectorReadyCandidate)
                    .toList();
        }
        if (sortedCandidates.size() > request.topK()) {
            sortedCandidates = sortedCandidates.subList(0, request.topK());
        }

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
            boolean filterBySkill,
            Float experienceWeight,
            Float skillWeight,
            boolean onlyVectorReadyCandidates
    ) {
        public SearchRequest {
            topK = clampTopK(topK);
        }

        public SearchRequest(String jobDescription, int topK, boolean filterByExperience, boolean filterBySkill) {
            this(jobDescription, topK, filterByExperience, filterBySkill, null, null, false);
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

    private static int clampTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private static int resolveChunkTopK(int candidateTopK) {
        long expanded = (long) candidateTopK * CHUNK_OVERSAMPLE_FACTOR;
        if (expanded <= 0L) {
            return candidateTopK;
        }
        return (int) Math.min(expanded, MAX_CHUNK_TOP_K);
    }

    /**
     * 根据筛选条件生成检索类型列表
     */
    private static ChunkType[] resolveChunkTypes(SearchRequest request) {
        if (request.filterByExperience() && request.filterBySkill()) {
            return new ChunkType[]{ChunkType.EXPERIENCE, ChunkType.SKILL};
        }
        if (request.filterByExperience()) {
            return new ChunkType[]{ChunkType.EXPERIENCE};
        }
        if (request.filterBySkill()) {
            return new ChunkType[]{ChunkType.SKILL};
        }
        return null;
    }

    /**
     * 按候选人聚合分块并计算综合分数
     */
    private static List<CandidateMatch> aggregateAndSort(
            List<VectorStoreService.SearchResult> results,
            WeightConfig weightConfig) {
        Map<UUID, CandidateMatch> candidateMatches = new LinkedHashMap<>();

        for (VectorStoreService.SearchResult r : results) {
            candidateMatches
                    .computeIfAbsent(r.candidateId(), cid -> new CandidateMatch(
                            cid,
                            new ArrayList<>(),
                            0.0f
                    ))
                    .matchedChunks()
                    .add(new MatchedChunk(
                            r.chunkType().name(),
                            r.content(),
                            r.score()
                    ));
        }

        return candidateMatches.entrySet().stream()
                .map(entry -> {
                    CandidateMatch match = entry.getValue();
                    float maxOverallScore = match.matchedChunks().stream()
                            .map(MatchedChunk::score)
                            .max(Float::compareTo)
                            .orElse(0.0f);

                    float maxExperienceScore = match.matchedChunks().stream()
                            .filter(c -> ChunkType.EXPERIENCE.name().equals(c.chunkType()))
                            .map(MatchedChunk::score)
                            .max(Float::compareTo)
                            .orElse(0.0f);

                    float maxSkillScore = match.matchedChunks().stream()
                            .filter(c -> ChunkType.SKILL.name().equals(c.chunkType()))
                            .map(MatchedChunk::score)
                            .max(Float::compareTo)
                            .orElse(0.0f);

                    float weightedScore = maxExperienceScore * weightConfig.experienceWeight()
                            + maxSkillScore * weightConfig.skillWeight();

                    // 兜底：当未命中 EXPERIENCE/SKILL 时，仍按原始最大分排序
                    if (weightedScore <= 0.0f) {
                        weightedScore = maxOverallScore;
                    }
                    return new CandidateMatch(entry.getKey(), match.matchedChunks(), weightedScore);
                })
                .sorted(Comparator.comparing(CandidateMatch::score).reversed())
                .toList();
    }

    private static WeightConfig resolveWeights(SearchRequest request, ChunkType[] types) {
        boolean includeExperience = types == null || Arrays.asList(types).contains(ChunkType.EXPERIENCE);
        boolean includeSkill = types == null || Arrays.asList(types).contains(ChunkType.SKILL);

        float experience = sanitizeWeight(request.experienceWeight(), includeExperience ? 0.5f : 0.0f);
        float skill = sanitizeWeight(request.skillWeight(), includeSkill ? 0.5f : 0.0f);

        if (!includeExperience) {
            experience = 0.0f;
        }
        if (!includeSkill) {
            skill = 0.0f;
        }

        // 单类型检索时该类型权重固定 1，避免分母归一化歧义
        if (includeExperience && !includeSkill) {
            return new WeightConfig(1.0f, 0.0f);
        }
        if (!includeExperience && includeSkill) {
            return new WeightConfig(0.0f, 1.0f);
        }

        float sum = experience + skill;
        if (sum <= 0.0f) {
            return new WeightConfig(0.5f, 0.5f);
        }
        return new WeightConfig(experience / sum, skill / sum);
    }

    private static float sanitizeWeight(Float value, float defaultValue) {
        if (value == null || !Float.isFinite(value) || value < 0.0f) {
            return defaultValue;
        }
        return value;
    }

    private boolean isVectorReadyCandidate(CandidateMatch candidate) {
        UUID candidateId = candidate.candidateId();
        if (candidateId == null) {
            return false;
        }
        boolean hasPendingOrProcessing = vectorIngestTaskRepository.existsByCandidateIdAndStatusIn(
                candidateId,
                List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING));
        if (hasPendingOrProcessing) {
            return false;
        }
        return vectorIngestTaskRepository.existsByCandidateIdAndStatus(candidateId, VectorIngestTaskStatus.DONE);
    }

    private record WeightConfig(float experienceWeight, float skillWeight) {
    }
}
