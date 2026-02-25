package com.walden.cvect.web.controller;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class JobDescriptionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobDescriptionJpaRepository jdRepository;

    @Autowired
    private CandidateJpaRepository candidateRepository;

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
    @DisplayName("should return 409 when JD still has candidates")
    void shouldReturn409WhenJdHasCandidates() throws Exception {
        JobDescription jd = jdRepository.save(new JobDescription("JD with candidate", "cannot delete"));
        candidateRepository.save(new Candidate(
                "resume.pdf",
                UUID.randomUUID().toString().replace("-", ""),
                "Alice",
                jd,
                "application/pdf",
                100L,
                50,
                false));

        mockMvc.perform(delete("/api/jds/{id}", jd.getId()))
                .andExpect(status().isConflict());

        assertTrue(jdRepository.findById(jd.getId()).isPresent());
    }
}
