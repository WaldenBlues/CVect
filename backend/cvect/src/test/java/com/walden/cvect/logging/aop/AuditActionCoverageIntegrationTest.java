package com.walden.cvect.logging.aop;

import com.walden.cvect.config.PostgresIntegrationTestBase;
import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.model.entity.AuditLog;
import com.walden.cvect.model.entity.AuthUser;
import com.walden.cvect.repository.AuditLogJpaRepository;
import com.walden.cvect.repository.AuthUserJpaRepository;
import com.walden.cvect.security.AuthService;
import com.walden.cvect.security.JwtService;
import com.walden.cvect.service.matching.SemanticSearchService;
import com.walden.cvect.web.controller.search.SearchController;
import com.walden.cvect.infra.vector.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.enabled=true",
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false",
        "app.embedding.service-url=http://127.0.0.1:65535/embed",
        "app.embedding.timeout-seconds=1"
})
@AutoConfigureMockMvc
@Tag("integration")
class AuditActionCoverageIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogJpaRepository auditLogRepository;

    @Autowired
    private AuthUserJpaRepository authUserRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private SemanticSearchService semanticSearchService;

    @MockBean
    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        when(semanticSearchService.search(any())).thenReturn(new SearchController.SearchResponse(0, 10, List.of()));
        when(vectorStoreService.getResolvedIndexType()).thenReturn("ivfflat");
        when(vectorStoreService.isOperational()).thenReturn(true);
        when(vectorStoreService.getAvailabilityMessage()).thenReturn(null);
    }

    @Test
    @DisplayName("login should persist audit log with actor fields and redacted password")
    void loginShouldPersistAuditLogWithActorFieldsAndRedactedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "username": "demo",
                                  "password": "demo123"
                                }
                                """.formatted(TenantConstants.DEFAULT_TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("demo"));

        AuditLog loginLog = latestAudit("login");
        assertThat(loginLog.getAction()).isEqualTo("login");
        assertThat(loginLog.getTarget()).isEqualTo("auth");
        assertThat(loginLog.getTenantId()).isEqualTo(TenantConstants.DEFAULT_TENANT_ID);
        assertThat(loginLog.getUserId()).isNotNull();
        assertThat(loginLog.getUsername()).isEqualTo("demo");
        assertThat(loginLog.getHttpMethod()).isEqualTo("POST");
        assertThat(loginLog.getRequestPath()).isEqualTo("/api/auth/login");
        assertThat(loginLog.getStatus()).isEqualTo("success");
        assertThat(loginLog.getCreatedAt()).isNotNull();
        assertThat(loginLog.getArgsSummary())
                .contains("username=\"demo\"")
                .contains("password=<redacted>")
                .doesNotContain("demo123");
    }

    @Test
    @DisplayName("authenticated search and vector operations should persist minimal audit records")
    void authenticatedSearchAndVectorOperationsShouldPersistMinimalAuditRecords() throws Exception {
        String token = bearerToken("demo");
        String rawQueryMarker = "RAW_QUERY_TEXT_SHOULD_NOT_BE_PERSISTED";

        mockMvc.perform(post("/api/auth/logout")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/search")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "%s",
                                  "topK": 10,
                                  "filterByExperience": true,
                                  "filterBySkill": true,
                                  "experienceWeight": 0.7,
                                  "skillWeight": 0.3,
                                  "onlyVectorReadyCandidates": false
                                }
                                """.formatted(rawQueryMarker.repeat(4))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/search/admin/create-index")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isOk());

        MvcResult healthResult = mockMvc.perform(get("/api/vector/health")
                        .header(AUTHORIZATION, token))
                .andReturn();
        assertThat(healthResult.getResponse().getStatus()).isIn(200, 503);

        AuditLog logoutLog = latestAudit("logout");
        assertThat(logoutLog.getUsername()).isEqualTo("demo");
        assertThat(logoutLog.getRequestPath()).isEqualTo("/api/auth/logout");
        assertThat(logoutLog.getStatus()).isEqualTo("success");

        AuditLog searchLog = latestAudit("semantic_search");
        assertThat(searchLog.getUsername()).isEqualTo("demo");
        assertThat(searchLog.getTarget()).isEqualTo("candidate_match");
        assertThat(searchLog.getRequestPath()).isEqualTo("/api/search");
        assertThat(searchLog.getStatus()).isEqualTo("success");
        assertThat(searchLog.getArgsSummary())
                .contains("jobDescription=len=")
                .contains("experienceWeight=0.7")
                .contains("skillWeight=0.3")
                .doesNotContain(rawQueryMarker);

        AuditLog createIndexLog = latestAudit("create_vector_index");
        assertThat(createIndexLog.getUsername()).isEqualTo("demo");
        assertThat(createIndexLog.getTarget()).isEqualTo("vector_index");
        assertThat(createIndexLog.getRequestPath()).isEqualTo("/api/search/admin/create-index");
        assertThat(createIndexLog.getStatus()).isEqualTo("success");

        AuditLog vectorHealthLog = latestAudit("check_vector_health");
        assertThat(vectorHealthLog.getUsername()).isEqualTo("demo");
        assertThat(vectorHealthLog.getTarget()).isEqualTo("vector");
        assertThat(vectorHealthLog.getRequestPath()).isEqualTo("/api/vector/health");
        assertThat(vectorHealthLog.getStatus()).isEqualTo("success");
    }

    private String bearerToken(String username) {
        AuthUser user = authUserRepository.findByTenantIdAndUsername(TenantConstants.DEFAULT_TENANT_ID, username)
                .orElseThrow();
        return "Bearer " + jwtService.createToken(authService.toCurrentUser(user));
    }

    private AuditLog latestAudit(String action) {
        return auditLogRepository.findAll().stream()
                .filter(log -> action.equals(log.getAction()))
                .max(Comparator.comparing(AuditLog::getCreatedAt))
                .orElseThrow(() -> new AssertionError("Missing audit log for action=" + action));
    }
}
