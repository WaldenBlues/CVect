package com.walden.cvect.web.controller.candidate;

import com.walden.cvect.config.PostgresIntegrationTestBase;
import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.model.entity.AuthUser;
import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateMatchScore;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.model.entity.Contact;
import com.walden.cvect.model.entity.Education;
import com.walden.cvect.model.entity.Honor;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.Link;
import com.walden.cvect.repository.AuthUserJpaRepository;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.ContactJpaRepository;
import com.walden.cvect.repository.EducationJpaRepository;
import com.walden.cvect.repository.HonorJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.LinkJpaRepository;
import com.walden.cvect.security.AuthService;
import com.walden.cvect.security.CurrentUser;
import com.walden.cvect.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.enabled=true",
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class CandidateDetailControllerSecurityIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateJpaRepository candidateRepository;

    @Autowired
    private CandidateMatchScoreJpaRepository candidateMatchScoreRepository;

    @Autowired
    private ContactJpaRepository contactRepository;

    @Autowired
    private EducationJpaRepository educationRepository;

    @Autowired
    private HonorJpaRepository honorRepository;

    @Autowired
    private LinkJpaRepository linkRepository;

    @Autowired
    private JobDescriptionJpaRepository jobDescriptionRepository;

    @Autowired
    private AuthUserJpaRepository authUserRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("detail should return visible candidate data for tenant-wide reader")
    void detailShouldReturnVisibleCandidateDataForTenantWideReader() throws Exception {
        AuthUser hrUser = authUserRepository.findByTenantIdAndUsername(TenantConstants.DEFAULT_TENANT_ID, "hr")
                .orElseThrow();
        JobDescription jd = new JobDescription(TenantConstants.DEFAULT_TENANT_ID, "JD detail", "content", hrUser.getId());
        jd = jobDescriptionRepository.save(jd);

        Candidate candidate = newCandidate(jd, "Alice");
        candidate.setRecruitmentStatus(CandidateRecruitmentStatus.TO_INTERVIEW);
        candidate = candidateRepository.save(candidate);

        contactRepository.save(new Contact(candidate.getId(), "EMAIL", "alice@example.com"));
        contactRepository.save(new Contact(candidate.getId(), "PHONE", "13800000000"));
        educationRepository.save(new Education(candidate.getId(), "Tsinghua University", "Computer Science", "Bachelor"));
        honorRepository.save(new Honor(candidate.getId(), "ACM Gold Medal"));
        linkRepository.save(new Link(candidate.getId(), "https://github.com/alice"));
        candidateMatchScoreRepository.save(new CandidateMatchScore(
                TenantConstants.DEFAULT_TENANT_ID,
                candidate.getId(),
                jd.getId(),
                0.72f,
                0.80f,
                0.64f,
                LocalDateTime.of(2026, 5, 4, 12, 0)));

        mockMvc.perform(get("/api/candidates/{id}", candidate.getId())
                        .header(AUTHORIZATION, bearerToken("hr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(candidate.getId().toString()))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.contact.emails[0]").value("alice@example.com"))
                .andExpect(jsonPath("$.contact.phones[0]").value("13800000000"))
                .andExpect(jsonPath("$.education[0]").value("Tsinghua University"))
                .andExpect(jsonPath("$.honor[0]").value("ACM Gold Medal"))
                .andExpect(jsonPath("$.externalLinks[0]").value("https://github.com/alice"))
                .andExpect(jsonPath("$.uploadFile.sourceFileName").value("alice.pdf"))
                .andExpect(jsonPath("$.uploadFile.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.uploadFile.fileSizeBytes").value(1234))
                .andExpect(jsonPath("$.uploadFile.parsedCharCount").value(321))
                .andExpect(jsonPath("$.uploadFile.truncated").value(false))
                .andExpect(jsonPath("$.recruitmentStatus").value("TO_INTERVIEW"))
                .andExpect(jsonPath("$.persistedMatchScore.overallScore").value(0.72))
                .andExpect(jsonPath("$.persistedMatchScore.scoredAt").value("2026-05-04T12:00:00"))
                .andExpect(jsonPath("$.experiences").doesNotExist())
                .andExpect(jsonPath("$.skills").doesNotExist());
    }

    @Test
    @DisplayName("detail should return 404 when recruiter is outside candidate data scope")
    void detailShouldReturn404WhenRecruiterIsOutsideCandidateDataScope() throws Exception {
        AuthUser hrUser = authUserRepository.findByTenantIdAndUsername(TenantConstants.DEFAULT_TENANT_ID, "hr")
                .orElseThrow();
        JobDescription jd = jobDescriptionRepository.save(
                new JobDescription(TenantConstants.DEFAULT_TENANT_ID, "JD scope", "content", hrUser.getId()));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Bob"));

        mockMvc.perform(get("/api/candidates/{id}", candidate.getId())
                        .header(AUTHORIZATION, bearerToken("recruiter")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("detail should return 403 when user lacks candidate read permission")
    void detailShouldReturn403WhenUserLacksCandidateReadPermission() throws Exception {
        AuthUser hrUser = authUserRepository.findByTenantIdAndUsername(TenantConstants.DEFAULT_TENANT_ID, "hr")
                .orElseThrow();
        JobDescription jd = jobDescriptionRepository.save(
                new JobDescription(TenantConstants.DEFAULT_TENANT_ID, "JD forbidden", "content", hrUser.getId()));
        Candidate candidate = candidateRepository.save(newCandidate(jd, "Carol"));

        AuthUser noPermissionUser = authUserRepository.save(new AuthUser(
                TenantConstants.DEFAULT_TENANT_ID,
                "no-read-" + UUID.randomUUID(),
                passwordEncoder.encode("demo123"),
                "No Read",
                true));

        CurrentUser currentUser = authService.toCurrentUser(authUserRepository.findWithRolesById(noPermissionUser.getId())
                .orElseThrow());

        mockMvc.perform(get("/api/candidates/{id}", candidate.getId())
                        .header(AUTHORIZATION, "Bearer " + jwtService.createToken(currentUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detail should return 404 for non-existent candidate")
    void detailShouldReturn404ForNonExistentCandidate() throws Exception {
        mockMvc.perform(get("/api/candidates/{id}", UUID.randomUUID())
                        .header(AUTHORIZATION, bearerToken("hr")))
                .andExpect(status().isNotFound());
    }

    private String bearerToken(String username) {
        AuthUser user = authUserRepository.findByTenantIdAndUsername(TenantConstants.DEFAULT_TENANT_ID, username)
                .orElseThrow();
        return "Bearer " + jwtService.createToken(authService.toCurrentUser(user));
    }

    private Candidate newCandidate(JobDescription jd, String name) {
        return new Candidate(
                jd.getTenantId(),
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
