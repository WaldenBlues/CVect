package com.walden.cvect.web.controller.search;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.logging.support.WebLogFormatter;
import com.walden.cvect.logging.web.GlobalExceptionHandler;
import com.walden.cvect.security.AuthService;
import com.walden.cvect.security.JwtAuthenticationFilter;
import com.walden.cvect.security.JwtService;
import com.walden.cvect.security.PermissionCodes;
import com.walden.cvect.security.PermissionGuard;
import com.walden.cvect.security.SecurityConfig;
import com.walden.cvect.service.matching.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SearchControllerTest.TestConfig.class, properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@DisplayName("SearchController MockMvc tests")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VectorStoreService vectorStore;

    @MockBean
    private SemanticSearchService semanticSearchService;

    @MockBean(name = "permissionGuard")
    private PermissionGuard permissionGuard;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(permissionGuard.has(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("shouldReturnSearchResultsWhenRequestIsValid")
    void shouldReturnSearchResultsWhenRequestIsValid() throws Exception {
        UUID candidateId = UUID.randomUUID();
        when(semanticSearchService.search(any())).thenReturn(new SearchController.SearchResponse(
                1,
                5,
                List.of(new SearchController.CandidateMatch(
                        candidateId,
                        List.of(new SearchController.MatchedChunk("SKILL", "Spring Boot", 0.91f)),
                        0.91f))));

        mockMvc.perform(post("/api/search")
                        .with(user("hr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "Java backend role",
                                  "topK": 5,
                                  "filterByExperience": true,
                                  "filterBySkill": true,
                                  "experienceWeight": 0.7,
                                  "skillWeight": 0.3,
                                  "onlyVectorReadyCandidates": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.requested").value(5))
                .andExpect(jsonPath("$.candidates[0].candidateId").value(candidateId.toString()))
                .andExpect(jsonPath("$.candidates[0].matchedChunks[0].chunkType").value("SKILL"))
                .andExpect(jsonPath("$.candidates[0].matchedChunks[0].content").value("Spring Boot"))
                .andExpect(jsonPath("$.candidates[0].score").value(0.91));

        verify(semanticSearchService).search(argThat(request ->
                "Java backend role".equals(request.jobDescription())
                        && request.topK() == 5
                        && request.filterByExperience()
                        && request.filterBySkill()
                        && request.onlyVectorReadyCandidates()));
    }

    @Test
    @DisplayName("shouldRejectBlankQuery")
    void shouldRejectBlankQuery() throws Exception {
        mockMvc.perform(post("/api/search")
                        .with(user("hr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "   ",
                                  "topK": 10,
                                  "filterByExperience": false,
                                  "filterBySkill": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad request"))
                .andExpect(jsonPath("$.path").value("/api/search"));

        verifyNoInteractions(semanticSearchService);
    }

    @Test
    @DisplayName("shouldClampInvalidTopKToDefault")
    void shouldClampInvalidTopKToDefault() throws Exception {
        when(semanticSearchService.search(any())).thenAnswer(invocation -> {
            SearchController.SearchRequest request = invocation.getArgument(0);
            return new SearchController.SearchResponse(0, request.topK(), List.of());
        });

        mockMvc.perform(post("/api/search")
                        .with(user("hr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "Java backend role",
                                  "topK": 0,
                                  "filterByExperience": false,
                                  "filterBySkill": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(10));

        verify(semanticSearchService).search(argThat(request -> request.topK() == 10));
    }

    @Test
    @DisplayName("shouldClampTopKWhenItExceedsUpperBound")
    void shouldClampTopKWhenItExceedsUpperBound() throws Exception {
        when(semanticSearchService.search(any())).thenAnswer(invocation -> {
            SearchController.SearchRequest request = invocation.getArgument(0);
            return new SearchController.SearchResponse(0, request.topK(), List.of());
        });

        mockMvc.perform(post("/api/search")
                        .with(user("hr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "Java backend role",
                                  "topK": 5000,
                                  "filterByExperience": false,
                                  "filterBySkill": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(200));

        verify(semanticSearchService).search(argThat(request -> request.topK() == 200));
    }

    @Test
    @DisplayName("shouldReturnInternalServerErrorWhenServiceFails")
    void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
        when(semanticSearchService.search(any())).thenThrow(new IllegalStateException("embedding unavailable"));

        mockMvc.perform(post("/api/search")
                        .with(user("hr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "Java backend role",
                                  "topK": 10,
                                  "filterByExperience": false,
                                  "filterBySkill": false
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.path").value("/api/search"));
    }

    @Test
    @DisplayName("shouldRejectUnauthenticatedRequest")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "Java backend role",
                                  "topK": 10,
                                  "filterByExperience": false,
                                  "filterBySkill": false
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(semanticSearchService);
    }

    @Test
    @DisplayName("shouldRejectAuthenticatedUserWithoutSearchPermission")
    void shouldRejectAuthenticatedUserWithoutSearchPermission() throws Exception {
        when(permissionGuard.has(PermissionCodes.SEARCH_RUN)).thenReturn(false);

        mockMvc.perform(post("/api/search")
                        .with(SecurityMockMvcRequestPostProcessors.user("recruiter"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "Java backend role",
                                  "topK": 10,
                                  "filterByExperience": false,
                                  "filterBySkill": false
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(semanticSearchService);
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @Import({SearchController.class, SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean
        LogProperties logProperties() {
            return new LogProperties();
        }

        @Bean
        WebLogFormatter webLogFormatter() {
            return new WebLogFormatter();
        }
    }
}
