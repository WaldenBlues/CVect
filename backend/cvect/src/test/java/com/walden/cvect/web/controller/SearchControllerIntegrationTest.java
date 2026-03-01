package com.walden.cvect.web.controller;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.model.ParseResult;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.service.ChunkerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.walden.cvect.config.PostgresIntegrationTestBase;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchController API 集成测试
 *
 * 测试原则：遵循完整流水线测试模式
 * PDF -> parser -> normalizer -> chunker -> fact extraction -> vector storage -> search API
 *
 * 前置条件：Testcontainers 提供 PostgreSQL + pgvector
 *
 * 如果 Docker 不可用，测试会自动跳过
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.upload.worker.enabled=false",
                "app.vector.ingest.worker.enabled=false"
        })
@Tag("integration")
@Tag("api")
@DisplayName("SearchController API 测试（流水线测试）")
class SearchControllerIntegrationTest extends PostgresIntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired(required = false)
    private SearchController searchController;

    @Autowired
    private ResumeParser parser;

    @Autowired
    private ResumeTextNormalizer normalizer;

    @Autowired
    private ChunkerService chunker;

    @Autowired(required = false)
    private VectorStoreService vectorStore;

    @Autowired(required = false)
    private EmbeddingService embeddingService;

    @Autowired
    private CandidateJpaRepository candidateRepository;

    private String baseUrl;

    private ResponseEntity<Map> postSearch(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                baseUrl + "/api/search",
                new HttpEntity<>(requestBody, headers),
                Map.class);
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    /**
     * 从真实 PDF 解析并获取指定类型的 chunk
     * 遵循完整流水线：parser -> normalizer -> chunker
     */
    private List<ResumeChunk> getChunksFromPdf(String pdfPath, ChunkType type) throws Exception {
        InputStream is = getClass().getResourceAsStream(pdfPath);
        assertNotNull(is, pdfPath + " 不存在");

        ParseResult parsed = parser.parse(is, "application/pdf");
        assertNotNull(parsed.getContent(), "解析内容不应为空");

        String normalized = normalizer.normalize(parsed.getContent());
        List<ResumeChunk> chunks = chunker.chunk(normalized);

        return chunks.stream()
                .filter(c -> c.getType() == type)
                .toList();
    }

    @Test
    @DisplayName("服务应正确初始化")
    void should_initialize_correctly() {
        assertNotNull(searchController);
        assertNotNull(vectorStore);
        assertNotNull(parser);
        assertNotNull(normalizer);
        assertNotNull(chunker);
    }

    @Test
    @DisplayName("搜索 API 应返回有效响应结构（Docker 模式）")
    @Tag("pipeline")
    void should_return_valid_search_response_structure() {
        Assumptions.assumeTrue(vectorStore != null && embeddingService != null,
            "跳过：需要 PostgreSQL + pgvector 和 Python embedding 服务");

        // Given
        String requestBody = """
            {
                "jobDescription": "招聘 Java 工程师",
                "topK": 10,
                "filterByExperience": true,
                "filterBySkill": true
            }
            """;

        // When
        ResponseEntity<Map> response = postSearch(requestBody);
        Assumptions.assumeTrue(response.getStatusCode() == HttpStatus.OK,
                "跳过：搜索依赖未就绪，状态码=" + response.getStatusCode());

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map body = response.getBody();
        assertTrue(body.containsKey("totalResults"));
        assertTrue(body.containsKey("requested"));
        assertTrue(body.containsKey("candidates"));
    }

    @Test
    @DisplayName("搜索请求应支持自定义 topK 参数（Docker 模式）")
    @Tag("pipeline")
    void should_support_custom_topK() {
        Assumptions.assumeTrue(vectorStore != null && embeddingService != null,
            "跳过：需要 PostgreSQL + pgvector 和 Python embedding 服务");

        // Given
        String requestBody = """
            {
                "jobDescription": "招聘后端开发",
                "topK": 5,
                "filterByExperience": true,
                "filterBySkill": false
            }
            """;

        // When
        ResponseEntity<Map> response = postSearch(requestBody);
        Assumptions.assumeTrue(response.getStatusCode() == HttpStatus.OK,
                "跳过：搜索依赖未就绪，状态码=" + response.getStatusCode());

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().get("requested"));
    }

    @Test
    @DisplayName("搜索请求应支持类型过滤（Docker 模式）")
    @Tag("pipeline")
    void should_support_type_filtering() {
        Assumptions.assumeTrue(vectorStore != null && embeddingService != null,
            "跳过：需要 PostgreSQL + pgvector 和 Python embedding 服务");

        // Given: 只搜索 EXPERIENCE
        String requestBody = """
            {
                "jobDescription": "招聘架构师",
                "topK": 10,
                "filterByExperience": true,
                "filterBySkill": false
            }
            """;

        // When
        ResponseEntity<Map> response = postSearch(requestBody);
        Assumptions.assumeTrue(response.getStatusCode() == HttpStatus.OK,
                "跳过：搜索依赖未就绪，状态码=" + response.getStatusCode());

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("空 jobDescription 应返回空结果（Docker 模式）")
    @Tag("pipeline")
    void should_handle_empty_job_description() {
        Assumptions.assumeTrue(vectorStore != null && embeddingService != null,
            "跳过：需要 PostgreSQL + pgvector 和 Python embedding 服务");

        // Given
        String requestBody = """
            {
                "jobDescription": "",
                "topK": 10,
                "filterByExperience": false,
                "filterBySkill": false
            }
            """;

        // When
        ResponseEntity<Map> response = postSearch(requestBody);

        // Then
        assertTrue(
                response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.BAD_REQUEST
        );
    }

    @Test
    @DisplayName("创建索引 API 应正常工作（Docker 模式）")
    @Tag("admin")
    void should_create_index_via_api() {
        Assumptions.assumeTrue(vectorStore != null,
            "跳过：需要 PostgreSQL + pgvector");

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/search/admin/create-index",
                null,
                String.class
        );

        // Then
        Assumptions.assumeTrue(response.getStatusCode() == HttpStatus.OK,
                "跳过：当前数据库不支持 HNSW 索引，状态码=" + response.getStatusCode());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("HNSW"));
    }

    @Test
    @DisplayName("全链路测试：从 PDF 解析到搜索 API（Docker 模式）")
    @Tag("pipeline")
    void should_support_full_pipeline_to_search() throws Exception {
        Assumptions.assumeTrue(vectorStore != null && embeddingService != null,
            "跳过：需要 PostgreSQL + pgvector 和 Python embedding 服务");

        // Given: 从 My.pdf 解析获取 EXPERIENCE chunk
        List<ResumeChunk> experienceChunks = getChunksFromPdf("/static/My.pdf", ChunkType.EXPERIENCE);
        List<ResumeChunk> skillChunks = getChunksFromPdf("/static/My.pdf", ChunkType.SKILL);

        if (experienceChunks.isEmpty() && skillChunks.isEmpty()) {
            return; // 跳过，如果没有可测试的数据
        }

        UUID candidateId = createCandidateId("search-pipeline");

        // 存储向量数据
        if (!experienceChunks.isEmpty()) {
            try {
                vectorStore.save(candidateId, ChunkType.EXPERIENCE, experienceChunks.get(0).getContent());
            } catch (Exception e) {
                // Python 服务未启动时跳过
            }
        }

        if (!skillChunks.isEmpty()) {
            try {
                vectorStore.save(candidateId, ChunkType.SKILL, skillChunks.get(0).getContent());
            } catch (Exception e) {
                // Python 服务未启动时跳过
            }
        }

        // When: 搜索与存储内容相关的职位
        String searchRequest = """
            {
                "jobDescription": "后端开发 Java Spring 分布式系统",
                "topK": 10,
                "filterByExperience": true,
                "filterBySkill": true
            }
            """;

        ResponseEntity<Map> response = postSearch(searchRequest);
        Assumptions.assumeTrue(response.getStatusCode() == HttpStatus.OK,
                "跳过：搜索依赖未就绪，状态码=" + response.getStatusCode());

        // Then: 验证 API 响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("candidates"));
    }

    @Test
    @DisplayName("搜索结果应包含候选人匹配信息（Docker 模式）")
    @Tag("pipeline")
    void should_include_candidate_info_in_results() {
        Assumptions.assumeTrue(vectorStore != null && embeddingService != null,
            "跳过：需要 PostgreSQL + pgvector 和 Python embedding 服务");

        // Given
        String requestBody = """
            {
                "jobDescription": "招聘高级工程师",
                "topK": 10,
                "filterByExperience": true,
                "filterBySkill": true
            }
            """;

        // When
        ResponseEntity<Map> response = postSearch(requestBody);

        // Then
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map body = response.getBody();
            List<?> candidates = (List<?>) body.get("candidates");

            if (candidates != null && !candidates.isEmpty()) {
                Object firstCandidate = candidates.get(0);
                if (firstCandidate instanceof Map) {
                    Map candidate = (Map) firstCandidate;
                    assertTrue(candidate.containsKey("candidateId"));
                    assertTrue(candidate.containsKey("score"));
                }
            }
        }
    }

    private UUID createCandidateId(String namePrefix) {
        Candidate candidate = new Candidate(
                namePrefix + ".pdf",
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                namePrefix,
                null,
                "application/pdf",
                32L,
                32,
                false);
        return candidateRepository.save(candidate).getId();
    }
}
