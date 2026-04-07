package com.walden.cvect.web.controller.upload;

import com.walden.cvect.config.PostgresIntegrationTestBase;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class UploadBatchControllerIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobDescriptionJpaRepository jobDescriptionRepository;

    @Autowired
    private UploadBatchJpaRepository batchRepository;

    @Autowired
    private UploadItemJpaRepository itemRepository;

    @Test
    @DisplayName("GET /api/uploads/batches/{id} should return summary counts")
    void shouldReturnBatchSummaryWithCounts() throws Exception {
        UploadBatch batch = createBatch();

        itemRepository.save(newItem(batch, "a.pdf", "DONE", null));
        itemRepository.save(newItem(batch, "b.pdf", "DUPLICATE", null));
        itemRepository.save(newItem(batch, "c.pdf", "FAILED", "parse failed"));
        itemRepository.save(newItem(batch, "d.pdf", "PROCESSING", null));
        itemRepository.save(newItem(batch, "e.pdf", "QUEUED", null));

        mockMvc.perform(get("/api/uploads/batches/{id}", batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(batch.getId().toString()))
                .andExpect(jsonPath("$.jdId").value(batch.getJobDescription().getId().toString()))
                .andExpect(jsonPath("$.counts.total").value(5))
                .andExpect(jsonPath("$.counts.succeeded").value(2))
                .andExpect(jsonPath("$.counts.failed").value(1))
                .andExpect(jsonPath("$.counts.processing").value(1))
                .andExpect(jsonPath("$.counts.pending").value(1));
    }

    @Test
    @DisplayName("GET /api/uploads/batches/{id}/items should support status filter and pagination")
    void shouldReturnBatchItemsByStatusWithPagination() throws Exception {
        UploadBatch batch = createBatch();

        itemRepository.save(newItem(batch, "f1.pdf", "FAILED", "e1"));
        itemRepository.save(newItem(batch, "f2.pdf", "FAILED", "e2"));
        itemRepository.save(newItem(batch, "f3.pdf", "FAILED", "e3"));
        itemRepository.save(newItem(batch, "ok.pdf", "DONE", null));

        mockMvc.perform(get("/api/uploads/batches/{id}/items", batch.getId())
                        .param("status", "FAILED")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[1].status").value("FAILED"));
    }

    @Test
    @DisplayName("GET /api/uploads/batches/{id}/items should accept legacy status aliases")
    void shouldAcceptLegacyStatusAliases() throws Exception {
        UploadBatch batch = createBatch();
        itemRepository.save(newItem(batch, "queued.pdf", "QUEUED", null));
        itemRepository.save(newItem(batch, "done.pdf", "DONE", null));

        mockMvc.perform(get("/api/uploads/batches/{id}/items", batch.getId())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("queued.pdf"))
                .andExpect(jsonPath("$.content[0].status").value("QUEUED"));

        mockMvc.perform(get("/api/uploads/batches/{id}/items", batch.getId())
                        .param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("done.pdf"))
                .andExpect(jsonPath("$.content[0].status").value("DONE"));
    }

    @Test
    @DisplayName("POST /api/uploads/batches/{id}/retry-failed should be idempotent")
    void retryFailedShouldBeIdempotent() throws Exception {
        UploadBatch batch = createBatch();

        UploadItem firstFailed = newItem(batch, "failed-a.pdf", "FAILED", "e1");
        firstFailed.setAttempt(0);
        firstFailed = itemRepository.save(firstFailed);

        UploadItem secondFailed = newItem(batch, "failed-b.pdf", "FAILED", "e2");
        secondFailed.setAttempt(2);
        secondFailed = itemRepository.save(secondFailed);

        itemRepository.save(newItem(batch, "done.pdf", "DONE", null));

        mockMvc.perform(post("/api/uploads/batches/{id}/retry-failed", batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batch.getId().toString()))
                .andExpect(jsonPath("$.retriedCount").value(2));

        UploadItem afterFirstA = itemRepository.findById(firstFailed.getId()).orElseThrow();
        UploadItem afterFirstB = itemRepository.findById(secondFailed.getId()).orElseThrow();
        assertEquals(UploadItemStatus.QUEUED, afterFirstA.getStatus());
        assertEquals(UploadItemStatus.QUEUED, afterFirstB.getStatus());
        assertEquals(1, afterFirstA.getAttempt());
        assertEquals(3, afterFirstB.getAttempt());
        assertNotNull(afterFirstA.getQueueJobKey());
        assertNotNull(afterFirstB.getQueueJobKey());
        String firstQueueJobKeyA = afterFirstA.getQueueJobKey();
        String firstQueueJobKeyB = afterFirstB.getQueueJobKey();

        mockMvc.perform(post("/api/uploads/batches/{id}/retry-failed", batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batch.getId().toString()))
                .andExpect(jsonPath("$.retriedCount").value(0));

        UploadItem afterSecondA = itemRepository.findById(firstFailed.getId()).orElseThrow();
        UploadItem afterSecondB = itemRepository.findById(secondFailed.getId()).orElseThrow();
        assertEquals(UploadItemStatus.QUEUED, afterSecondA.getStatus());
        assertEquals(UploadItemStatus.QUEUED, afterSecondB.getStatus());
        assertEquals(1, afterSecondA.getAttempt());
        assertEquals(3, afterSecondB.getAttempt());
        assertEquals(firstQueueJobKeyA, afterSecondA.getQueueJobKey());
        assertEquals(firstQueueJobKeyB, afterSecondB.getQueueJobKey());
    }

    @Test
    @DisplayName("batch not found should return 404")
    void shouldReturn404WhenBatchNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/uploads/batches/{id}", id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/uploads/batches/{id}/items", id))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/uploads/batches/{id}/retry-failed", id))
                .andExpect(status().isNotFound());
    }

    private UploadBatch createBatch() {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Upload test JD", "for upload batch tests"));
        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 0));
        assertNotNull(batch.getId());
        return batch;
    }

    private UploadItem newItem(UploadBatch batch, String fileName, String status, String errorMessage) {
        UploadItem item = new UploadItem(batch, fileName);
        UploadItemStatus uploadItemStatus = UploadItemStatus.parseOrNull(status);
        if (uploadItemStatus == null) {
            throw new IllegalArgumentException("Unsupported test status: " + status);
        }
        item.setStatus(uploadItemStatus);
        item.setErrorMessage(errorMessage);
        if ("FAILED".equals(status)) {
            item.setStoragePath("storage/mock-" + fileName);
        }
        return item;
    }
}
