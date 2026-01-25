package com.walden.cvect.web.controller;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SearchController 单元测试 - 使用 Mockito 模拟依赖
 * 测试原则：隔离外部依赖，快速验证业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchController 单元测试")
class SearchControllerTest {

    @Mock
    private VectorStoreService vectorStore;

    @Mock
    private EmbeddingService embeddingService;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController(vectorStore, embeddingService);
    }

    @Test
    @DisplayName("搜索请求应返回按分数排序的候选人列表")
    void should_return_sorted_candidates_by_score() {
        // Given
        UUID candidate1 = UUID.randomUUID();
        UUID candidate2 = UUID.randomUUID();
        
        float[] dummyEmbedding = new float[768];
        when(embeddingService.embed("Java工程师招聘"))
                .thenReturn(dummyEmbedding);
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(vectorStore.search(dummyEmbedding, 10, new ChunkType[]{ChunkType.EXPERIENCE, ChunkType.SKILL}))
                .thenReturn(List.of(
                        new VectorStoreService.SearchResult(id1, candidate1, ChunkType.EXPERIENCE, "Java开发经验", 0.2f),
                        new VectorStoreService.SearchResult(id2, candidate1, ChunkType.SKILL, "Spring Boot", 0.1f),
                        new VectorStoreService.SearchResult(id3, candidate2, ChunkType.EXPERIENCE, "Python开发", 0.3f)
                ));

        // When
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Java工程师招聘", 10, true, true
        );
        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        SearchController.SearchResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.totalResults());
        assertEquals(10, body.requested());
        
        List<SearchController.CandidateMatch> candidates = body.candidates();
        assertEquals(2, candidates.size());
        
        // 候选人1应该有最高分 0.9
        SearchController.CandidateMatch first = candidates.get(0);
        assertEquals(candidate1, first.candidateId());
        assertEquals(0.9f, first.score(), 0.001f);
        assertEquals(2, first.matchedChunks().size());
        
        // 候选人2分数 0.7
        SearchController.CandidateMatch second = candidates.get(1);
        assertEquals(candidate2, second.candidateId());
        assertEquals(0.7f, second.score(), 0.001f);
        assertEquals(1, second.matchedChunks().size());
    }

    @Test
    @DisplayName("应支持仅筛选 EXPERIENCE 类型")
    void should_filter_by_experience_only() {
        // Given
        float[] dummyEmbedding = new float[768];
        when(embeddingService.embed("招聘后端开发"))
                .thenReturn(dummyEmbedding);
        
        when(vectorStore.search(dummyEmbedding, 5, new ChunkType[]{ChunkType.EXPERIENCE}))
                .thenReturn(List.of());

        // When
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "招聘后端开发", 5, true, false
        );
        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalResults());
    }

    @Test
    @DisplayName("应支持仅筛选 SKILL 类型")
    void should_filter_by_skill_only() {
        // Given
        float[] dummyEmbedding = new float[768];
        when(embeddingService.embed("需要Java技能"))
                .thenReturn(dummyEmbedding);
        
        when(vectorStore.search(dummyEmbedding, 3, new ChunkType[]{ChunkType.SKILL}))
                .thenReturn(List.of());

        // When
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "需要Java技能", 3, false, true
        );
        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalResults());
    }

    @Test
    @DisplayName("当不筛选任何类型时应搜索所有类型")
    void should_search_all_types_when_no_filter() {
        // Given
        float[] dummyEmbedding = new float[768];
        when(embeddingService.embed("通用搜索"))
                .thenReturn(dummyEmbedding);
        
        when(vectorStore.search(dummyEmbedding, 10, null))
                .thenReturn(List.of());

        // When
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "通用搜索", 10, false, false
        );
        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalResults());
    }

    @Test
    @DisplayName("topK 参数应在合理范围内自动调整")
    void should_adjust_topK_within_bounds() {
        // Given: topK 0 应调整为 10, topK 150 应调整为 100
        float[] dummyEmbedding = new float[768];
        when(embeddingService.embed("测试"))
                .thenReturn(dummyEmbedding);
        
        // 测试 topK=0 → 调整为10
        when(vectorStore.search(dummyEmbedding, 10, new ChunkType[]{ChunkType.EXPERIENCE, ChunkType.SKILL}))
                .thenReturn(List.of());

        // When
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "测试", 0, true, true
        );
        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().requested());
    }
}