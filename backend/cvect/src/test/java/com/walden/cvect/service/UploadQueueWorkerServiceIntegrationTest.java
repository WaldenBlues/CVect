package com.walden.cvect.service;

import com.walden.cvect.config.PostgresIntegrationTestBase;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadBatchStatus;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=true",
        "app.upload.worker.initial-delay-ms=600000",
        "app.upload.worker.fixed-delay-ms=600000",
        "app.upload.worker.stale-processing-ms=0"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class UploadQueueWorkerServiceIntegrationTest extends PostgresIntegrationTestBase {

    private static final Path STORAGE_DIR = Paths.get("storage");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadQueueWorkerService workerService;

    @Autowired
    private JobDescriptionJpaRepository jobDescriptionRepository;

    @Autowired
    private UploadBatchJpaRepository batchRepository;

    @Autowired
    private UploadItemJpaRepository itemRepository;

    @Test
    @DisplayName("retry-failed queued item should be consumed by db worker")
    void queuedItemShouldBeConsumedByWorker() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Worker JD", "worker content"));
        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 1));

        Files.createDirectories(STORAGE_DIR);
        Path source = STORAGE_DIR.resolve("retry-worker-" + UUID.randomUUID() + ".txt");
        byte[] bytes = ("worker-retry-content-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        Files.write(source, bytes);

        UploadItem failed = new UploadItem(batch, "retry-worker.txt");
        failed.setStatus(UploadItemStatus.FAILED);
        failed.setErrorMessage("first parse failed");
        failed.setStoragePath(source.toString());
        failed.setAttempt(0);
        failed = itemRepository.save(failed);

        mockMvc.perform(post("/api/uploads/batches/{id}/retry-failed", batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retriedCount").value(1));

        UploadItem queued = itemRepository.findById(failed.getId()).orElseThrow();
        assertEquals(UploadItemStatus.QUEUED, queued.getStatus());
        assertEquals(1, queued.getAttempt());
        assertNotNull(queued.getQueueJobKey());

        workerService.consumeQueuedItems();

        UploadItem finished = itemRepository.findById(failed.getId()).orElseThrow();
        assertEquals(UploadItemStatus.DONE, finished.getStatus());
        assertNotNull(finished.getCandidateId());
        assertNotNull(finished.getStoragePath());
        assertTrue(Files.exists(Paths.get(finished.getStoragePath())));
        assertNull(finished.getQueueJobKey());

        UploadBatch refreshedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(UploadBatchStatus.DONE, refreshedBatch.getStatus());
        assertEquals(1, refreshedBatch.getProcessedFiles());
    }

    @Test
    @DisplayName("stale PROCESSING item should be recovered and consumed")
    void staleProcessingItemShouldBeRecoveredAndConsumed() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Worker stale JD", "worker content"));
        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 1));

        Files.createDirectories(STORAGE_DIR);
        Path source = STORAGE_DIR.resolve("stale-worker-" + UUID.randomUUID() + ".txt");
        byte[] bytes = ("stale-worker-content-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        Files.write(source, bytes);

        UploadItem stuck = new UploadItem(batch, "stale-worker.txt");
        stuck.setStatus(UploadItemStatus.PROCESSING);
        stuck.setStoragePath(source.toString());
        stuck.setAttempt(0);
        stuck.setQueueJobKey("stale-lease-" + UUID.randomUUID());
        stuck = itemRepository.save(stuck);

        workerService.consumeQueuedItems();

        UploadItem finished = itemRepository.findById(stuck.getId()).orElseThrow();
        assertEquals(UploadItemStatus.DONE, finished.getStatus());
        assertNotNull(finished.getCandidateId());
        assertNull(finished.getQueueJobKey());

        UploadBatch refreshedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(UploadBatchStatus.DONE, refreshedBatch.getStatus());
        assertEquals(1, refreshedBatch.getProcessedFiles());
    }

    @Test
    @DisplayName("queued item without job key should be auto-assigned and consumed")
    void queuedItemWithoutJobKeyShouldBeConsumed() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Worker queued-no-key JD", "worker content"));
        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 1));

        Files.createDirectories(STORAGE_DIR);
        Path source = STORAGE_DIR.resolve("queued-no-key-" + UUID.randomUUID() + ".txt");
        byte[] bytes = ("queued-no-key-content-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        Files.write(source, bytes);

        UploadItem queued = new UploadItem(batch, "queued-no-key.txt");
        queued.setStatus(UploadItemStatus.QUEUED);
        queued.setStoragePath(source.toString());
        queued.setAttempt(0);
        queued.setQueueJobKey(null);
        queued = itemRepository.save(queued);

        workerService.consumeQueuedItems();

        UploadItem finished = itemRepository.findById(queued.getId()).orElseThrow();
        assertEquals(UploadItemStatus.DONE, finished.getStatus());
        assertNotNull(finished.getCandidateId());
        assertNull(finished.getQueueJobKey());
    }
}
