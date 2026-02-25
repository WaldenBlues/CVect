package com.walden.cvect.web.controller;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateJpaRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class CandidateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateJpaRepository candidateRepository;

    @Autowired
    private JobDescriptionJpaRepository jdRepository;

    @Test
    @DisplayName("list candidates should include default recruitment status")
    void listShouldIncludeDefaultRecruitmentStatus() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD default status", "content"));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Alice"));

        mockMvc.perform(get("/api/candidates").param("jdId", jd.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$[0].recruitmentStatus").value("TO_CONTACT"));
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
