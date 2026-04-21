package com.walden.cvect.web.controller.search;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.logging.aop.TimedAction;
import com.walden.cvect.service.matching.SemanticSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 候选人搜索 API - 基于向量相似度
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {
    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 1000;

    private final VectorStoreService vectorStore;
    private final SemanticSearchService semanticSearchService;

    public SearchController(
            VectorStoreService vectorStore,
            SemanticSearchService semanticSearchService) {
        this.vectorStore = vectorStore;
        this.semanticSearchService = semanticSearchService;
    }

    /**
     * 根据职位描述搜索匹配的候选人
     *
     * @param request 搜索请求
     * @return 按相似度排序的候选人列表
     */
    @PostMapping
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).SEARCH_RUN)")
    @TimedAction(
            metric = "cvect.search.request",
            description = "End-to-end semantic search request latency",
            tags = {"layer", "controller"})
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        return ResponseEntity.ok(semanticSearchService.search(request));
    }

    /**
     * 创建配置的向量索引 (管理接口)
     */
    @PostMapping("/admin/create-index")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).SYSTEM_ADMIN)")
    public ResponseEntity<String> createIndex() {
        vectorStore.createVectorIndex();
        String indexType = vectorStore.getResolvedIndexType();
        return ResponseEntity.ok(indexType.toUpperCase(Locale.ROOT) + " index created successfully");
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

}
