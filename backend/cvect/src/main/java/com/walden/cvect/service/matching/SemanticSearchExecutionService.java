package com.walden.cvect.service.matching;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.logging.aop.TimedAction;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.web.controller.search.SearchController;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SemanticSearchExecutionService {
    private static final int CHUNK_OVERSAMPLE_FACTOR = 4;
    private static final int MAX_CHUNK_TOP_K = 4000;

    private final VectorStoreService vectorStore;
    private final SearchQueryEmbeddingCacheService queryEmbeddingCache;
    private final VectorIngestTaskJpaRepository vectorIngestTaskRepository;
    private final CandidateJpaRepository candidateRepository;
    private final CurrentUserService currentUserService;

    public SemanticSearchExecutionService(
            VectorStoreService vectorStore,
            SearchQueryEmbeddingCacheService queryEmbeddingCache,
            VectorIngestTaskJpaRepository vectorIngestTaskRepository,
            CandidateJpaRepository candidateRepository,
            CurrentUserService currentUserService) {
        this.vectorStore = vectorStore;
        this.queryEmbeddingCache = queryEmbeddingCache;
        this.vectorIngestTaskRepository = vectorIngestTaskRepository;
        this.candidateRepository = candidateRepository;
        this.currentUserService = currentUserService;
    }

    @TimedAction(
            metric = "cvect.search.compute",
            description = "Semantic search compute latency for cache-miss executions",
            tags = {"layer", "service"})
    public SearchController.SearchResponse search(SearchController.SearchRequest request) {
        float[] queryEmbedding = queryEmbeddingCache.get(request.jobDescription());
        ChunkType[] types = resolveChunkTypes(request);
        SearchWeightNormalizer.Weights weightConfig = SearchWeightNormalizer.resolve(request);
        List<UUID> tenantCandidateIds = candidateRepository.findIdsByTenantId(currentUserService.currentTenantId());
        if (tenantCandidateIds.isEmpty()) {
            return new SearchController.SearchResponse(0, request.topK(), List.of());
        }

        int chunkTopK = resolveChunkTopK(request.topK());
        List<VectorStoreService.SearchResult> results = vectorStore.search(queryEmbedding, chunkTopK, tenantCandidateIds, types);

        List<SearchController.CandidateMatch> sortedCandidates = aggregateAndSort(results, weightConfig);
        if (request.onlyVectorReadyCandidates()) {
            sortedCandidates = filterVectorReadyCandidates(sortedCandidates);
        }
        if (sortedCandidates.size() > request.topK()) {
            sortedCandidates = sortedCandidates.subList(0, request.topK());
        }

        return new SearchController.SearchResponse(
                sortedCandidates.size(),
                request.topK(),
                List.copyOf(sortedCandidates));
    }

    private static int resolveChunkTopK(int candidateTopK) {
        long expanded = (long) candidateTopK * CHUNK_OVERSAMPLE_FACTOR;
        if (expanded <= 0L) {
            return candidateTopK;
        }
        return (int) Math.min(expanded, MAX_CHUNK_TOP_K);
    }

    private static ChunkType[] resolveChunkTypes(SearchController.SearchRequest request) {
        if (request.filterByExperience() && request.filterBySkill()) {
            return new ChunkType[] {ChunkType.EXPERIENCE, ChunkType.SKILL};
        }
        if (request.filterByExperience()) {
            return new ChunkType[] {ChunkType.EXPERIENCE};
        }
        if (request.filterBySkill()) {
            return new ChunkType[] {ChunkType.SKILL};
        }
        return null;
    }

    private static List<SearchController.CandidateMatch> aggregateAndSort(
            List<VectorStoreService.SearchResult> results,
            SearchWeightNormalizer.Weights weightConfig) {
        Map<UUID, SearchController.CandidateMatch> candidateMatches = new LinkedHashMap<>();

        for (VectorStoreService.SearchResult result : results) {
            candidateMatches
                    .computeIfAbsent(result.candidateId(), candidateId -> new SearchController.CandidateMatch(
                            candidateId,
                            new ArrayList<>(),
                            0.0f))
                    .matchedChunks()
                    .add(new SearchController.MatchedChunk(
                            result.chunkType().name(),
                            result.content(),
                            result.score()));
        }

        return candidateMatches.entrySet().stream()
                .map(entry -> {
                    SearchController.CandidateMatch match = entry.getValue();
                    float maxOverallScore = match.matchedChunks().stream()
                            .map(SearchController.MatchedChunk::score)
                            .max(Float::compareTo)
                            .orElse(0.0f);

                    float maxExperienceScore = match.matchedChunks().stream()
                            .filter(chunk -> ChunkType.EXPERIENCE.name().equals(chunk.chunkType()))
                            .map(SearchController.MatchedChunk::score)
                            .max(Float::compareTo)
                            .orElse(0.0f);

                    float maxSkillScore = match.matchedChunks().stream()
                            .filter(chunk -> ChunkType.SKILL.name().equals(chunk.chunkType()))
                            .map(SearchController.MatchedChunk::score)
                            .max(Float::compareTo)
                            .orElse(0.0f);

                    float weightedScore = maxExperienceScore * weightConfig.experienceWeight()
                            + maxSkillScore * weightConfig.skillWeight();
                    if (weightedScore <= 0.0f) {
                        weightedScore = maxOverallScore;
                    }
                    return new SearchController.CandidateMatch(entry.getKey(), match.matchedChunks(), weightedScore);
                })
                .sorted(Comparator.comparing(SearchController.CandidateMatch::score).reversed())
                .toList();
    }

    private List<SearchController.CandidateMatch> filterVectorReadyCandidates(
            List<SearchController.CandidateMatch> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<UUID> candidateIds = candidates.stream()
                .map(SearchController.CandidateMatch::candidateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> inflightIds = new HashSet<>(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                candidateIds,
                List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING)));
        Set<UUID> doneIds = new HashSet<>(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                candidateIds,
                List.of(VectorIngestTaskStatus.DONE)));

        return candidates.stream()
                .filter(candidate -> {
                    UUID id = candidate.candidateId();
                    return id != null && doneIds.contains(id) && !inflightIds.contains(id);
                })
                .toList();
    }
}
