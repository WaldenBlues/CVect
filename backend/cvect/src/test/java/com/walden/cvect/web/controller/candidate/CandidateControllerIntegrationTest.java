package com.walden.cvect.web.controller.candidate;

import com.walden.cvect.config.PostgresIntegrationTestBase;
import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateMatchScore;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class CandidateControllerIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateJpaRepository candidateRepository;

    @Autowired
    private CandidateMatchScoreJpaRepository candidateMatchScoreRepository;

    @Autowired
    private JobDescriptionJpaRepository jdRepository;

    @Test
    @DisplayName("list candidates should include default recruitment status")
    void listShouldIncludeDefaultRecruitmentStatus() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD default status", "content"));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Alice"));

        mockMvc.perform(get("/api/candidates").param("jdId", jd.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Page-Size", "50"))
                .andExpect(jsonPath("$[0].candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$[0].recruitmentStatus").value("TO_CONTACT"))
                .andExpect(jsonPath("$[0].sourceFileName").value("alice.pdf"))
                .andExpect(jsonPath("$[0].baselineMatchScore").doesNotExist())
                .andExpect(jsonPath("$[0].fileSizeBytes").doesNotExist())
                .andExpect(jsonPath("$[0].emails").doesNotExist());
    }

    @Test
    @DisplayName("patch recruitment status should persist and return updated snapshot")
    void patchRecruitmentStatusShouldPersist() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD update status", "content"));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Bob"));

        mockMvc.perform(patch("/api/candidates/{id}/recruitment-status", candidate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recruitmentStatus\":\"TO_INTERVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$.status").value("UPDATED"))
                .andExpect(jsonPath("$.recruitmentStatus").value("TO_INTERVIEW"));

        Candidate saved = candidateRepository.findById(candidate.getId()).orElseThrow();
        assertEquals("TO_INTERVIEW", saved.getRecruitmentStatus().name());
    }

    @Test
    @DisplayName("patch recruitment status should return 404 for non-existent candidate")
    void patchRecruitmentStatusShouldReturn404() throws Exception {
        mockMvc.perform(patch("/api/candidates/{id}/recruitment-status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recruitmentStatus\":\"REJECTED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("list candidates should include persisted baseline match scores")
    void listShouldIncludePersistedBaselineMatchScores() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD baseline scores", "content"));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Carol"));
        candidateMatchScoreRepository.save(new CandidateMatchScore(
                candidate.getId(),
                jd.getId(),
                0.72f,
                0.80f,
                0.64f,
                java.time.LocalDateTime.now()));

        mockMvc.perform(get("/api/candidates").param("jdId", jd.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$[0].baselineMatchScore").value(0.72))
                .andExpect(jsonPath("$[0].baselineExperienceScore").doesNotExist())
                .andExpect(jsonPath("$[0].baselineSkillScore").doesNotExist())
                .andExpect(jsonPath("$[0].baselineScoredAt").doesNotExist());
    }

    @Test
    @DisplayName("candidate detail should include persisted match score breakdown")
    void detailShouldIncludePersistedMatchScoreBreakdown() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD detail scores", "content"));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Erin"));
        candidateMatchScoreRepository.save(new CandidateMatchScore(
                candidate.getId(),
                jd.getId(),
                0.72f,
                0.80f,
                0.64f,
                java.time.LocalDateTime.now()));

        mockMvc.perform(get("/api/candidates/{id}", candidate.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$.persistedMatchScore.overallScore").value(0.72))
                .andExpect(jsonPath("$.persistedMatchScore.experienceScore").value(0.8))
                .andExpect(jsonPath("$.persistedMatchScore.skillScore").value(0.64));
    }

    @Test
    @DisplayName("list candidates should support pagination and server-side filters")
    void listShouldSupportPaginationAndServerSideFilters() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD paged filters", "content"));
        Candidate alice = newCandidate(jd, "Alice");
        alice.setRecruitmentStatus(CandidateRecruitmentStatus.TO_INTERVIEW);
        candidateRepository.save(alice);

        Candidate bob = newCandidate(jd, "Bob");
        bob.setRecruitmentStatus(CandidateRecruitmentStatus.TO_CONTACT);
        candidateRepository.save(bob);

        mockMvc.perform(get("/api/candidates")
                        .param("jdId", jd.getId().toString())
                        .param("page", "0")
                        .param("size", "1")
                        .param("q", "alice")
                        .param("recruitmentStatus", "TO_INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Page-Size", "1"))
                .andExpect(header().string("X-Total-Pages", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].candidateId").value(alice.getId().toString()))
                .andExpect(jsonPath("$[0].recruitmentStatus").value("TO_INTERVIEW"));
    }

    @Test
    @DisplayName("vector status endpoint should return lightweight status payload")
    void vectorStatusEndpointShouldReturnLightweightStatusPayload() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD vector status", "content"));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Diana"));

        mockMvc.perform(get("/api/candidates/vector-status")
                        .param("candidateId", candidate.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$[0].vectorStatus").value("NONE"))
                .andExpect(jsonPath("$[0].noVectorChunk").value(true))
                .andExpect(jsonPath("$[0].name").doesNotExist());
    }

    private Candidate newCandidate(JobDescription jd, String name) {
        return new Candidate(
                name.toLowerCase() + ".pdf",
                randomHash(),
                name,
                jd,
                "application/pdf",
                1234L,
                321,
                false);
    }

    private String randomHash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
