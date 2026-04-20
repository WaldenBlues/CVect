package com.walden.cvect.web.controller.upload;

import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.web.sse.BatchStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class BatchSseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BatchStreamService batchStreamService;

    @Autowired
    private JobDescriptionJpaRepository jobDescriptionRepository;

    @Autowired
    private UploadBatchJpaRepository batchRepository;

    @Test
    @DisplayName("GET /api/sse/batches/{id} should subscribe batch stream")
    void canonicalSsePathShouldSubscribe() throws Exception {
        UUID batchId = createBatch().getId();
        when(batchStreamService.subscribe(batchId)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/sse/batches/{id}", batchId))
                .andExpect(status().isOk());

        verify(batchStreamService).subscribe(batchId);
    }

    @Test
    @DisplayName("GET /api/uploads/batches/{id}/stream should remain backward-compatible")
    void legacySsePathShouldSubscribe() throws Exception {
        UUID batchId = createBatch().getId();
        when(batchStreamService.subscribe(batchId)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/uploads/batches/{id}/stream", batchId))
                .andExpect(status().isOk());

        verify(batchStreamService).subscribe(batchId);
    }

    @Test
    @DisplayName("GET batch SSE should return 404 when batch is outside current tenant scope")
    void unknownBatchShouldReturn404() throws Exception {
        UUID batchId = UUID.randomUUID();

        mockMvc.perform(get("/api/sse/batches/{id}", batchId))
                .andExpect(status().isNotFound());

        verify(batchStreamService, never()).subscribe(batchId);
    }

    private UploadBatch createBatch() {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("SSE test JD", "for batch SSE tests"));
        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 0));
        assertNotNull(batch.getId());
        return batch;
    }
}
