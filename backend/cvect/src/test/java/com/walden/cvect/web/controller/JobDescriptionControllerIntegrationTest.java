package com.walden.cvect.web.controller;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateSnapshot;
import com.walden.cvect.config.PostgresIntegrationTestBase;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import com.walden.cvect.repository.CandidateSnapshotJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class JobDescriptionControllerIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobDescriptionJpaRepository jdRepository;

    @Autowired
    private CandidateJpaRepository candidateRepository;

    @Autowired
    private CandidateSnapshotJpaRepository snapshotRepository;

    @Autowired
    private UploadBatchJpaRepository batchRepository;

    @Autowired
    private UploadItemJpaRepository itemRepository;

    @Test
    @DisplayName("should delete JD when it has upload batches but no candidates")
    void shouldDeleteJdWhenOnlyUploadBatchesExist() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("Ops Retry", "retry case"));
        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 2));

        UploadItem item1 = new UploadItem(batch, "a.pdf");
        item1.setStatus(UploadItemStatus.FAILED);
        itemRepository.save(item1);

        UploadItem item2 = new UploadItem(batch, "b.pdf");
        item2.setStatus(UploadItemStatus.QUEUED);
        itemRepository.save(item2);

        mockMvc.perform(delete("/api/jds/{id}", jd.getId()))
                .andExpect(status().isNoContent());

        assertTrue(jdRepository.findById(jd.getId()).isEmpty());
        assertEquals(0L, batchRepository.countByJobDescriptionId(jd.getId()));

        mockMvc.perform(get("/api/uploads/batches/{id}", batch.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should delete JD together with candidates and snapshots")
    void shouldDeleteJdWhenCandidatesExist() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD with candidate", "cannot delete"));
        Candidate candidate = candidateRepository.save(new Candidate(
                "resume.pdf",
                UUID.randomUUID().toString().replace("-", ""),
                "Alice",
                jd,
                "application/pdf",
                100L,
                50,
                false));
        CandidateSnapshot snapshot = new CandidateSnapshot(candidate.getId());
        snapshot.setJdId(jd.getId());
        snapshot.setRecruitmentStatus(candidate.getRecruitmentStatus().name());
        snapshot.setName(candidate.getName());
        snapshot.setSourceFileName(candidate.getSourceFileName());
        snapshot.setContentType(candidate.getContentType());
        snapshot.setFileSizeBytes(candidate.getFileSizeBytes());
        snapshot.setParsedCharCount(candidate.getParsedCharCount());
        snapshot.setTruncated(candidate.getTruncated());
        snapshot.setCandidateCreatedAt(candidate.getCreatedAt());
        snapshot.setEmailsJson("[]");
        snapshot.setPhonesJson("[]");
        snapshot.setEducationsJson("[]");
        snapshot.setHonorsJson("[]");
        snapshot.setLinksJson("[]");
        snapshotRepository.save(snapshot);

        mockMvc.perform(delete("/api/jds/{id}", jd.getId()))
                .andExpect(status().isNoContent());

        assertTrue(jdRepository.findById(jd.getId()).isEmpty());
        assertEquals(0L, candidateRepository.countByJobDescriptionId(jd.getId()));
        assertTrue(snapshotRepository.findByJdIdOrderByCandidateCreatedAtDesc(jd.getId()).isEmpty());
    }

    @Test
    @DisplayName("should return candidateCount for JD list")
    void shouldReturnCandidateCountInList() throws Exception {
        JobDescription jd1 = jdRepository.save(new JobDescription("JD-1", "with candidate"));
        JobDescription jd2 = jdRepository.save(new JobDescription("JD-2", "without candidate"));
        candidateRepository.save(new Candidate(
                "resume-jd1.pdf",
                UUID.randomUUID().toString().replace("-", ""),
                "Bob",
                jd1,
                "application/pdf",
                100L,
                50,
                false));

        mockMvc.perform(get("/api/jds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + jd1.getId() + "')].candidateCount", hasItem(1)))
                .andExpect(jsonPath("$[?(@.id=='" + jd2.getId() + "')].candidateCount", hasItem(0)));
    }
}
