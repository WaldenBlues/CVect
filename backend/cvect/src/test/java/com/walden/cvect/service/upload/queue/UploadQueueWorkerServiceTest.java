package com.walden.cvect.service.upload.queue;

import com.walden.cvect.infra.storage.FileStorageService;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import com.walden.cvect.service.resume.ResumeProcessService;
import com.walden.cvect.web.sse.BatchStreamEvent;
import com.walden.cvect.web.sse.BatchStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadQueueWorkerService unit tests")
class UploadQueueWorkerServiceTest {

    @Mock
    private UploadItemJpaRepository itemRepository;
    @Mock
    private UploadBatchJpaRepository batchRepository;
    @Mock
    private ResumeProcessService resumeProcessService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private BatchStreamService batchStreamService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("processClaimedItem should mark upload task failed and record the failure reason when resume processing throws")
    void shouldMarkClaimedItemFailedWhenResumeProcessingThrows() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID jdId = UUID.randomUUID();

        JobDescription jobDescription = new JobDescription("Queue JD", "worker content");
        ReflectionTestUtils.setField(jobDescription, "id", jdId);

        UploadBatch batch = new UploadBatch(jobDescription, 1);
        ReflectionTestUtils.setField(batch, "id", batchId);
        batch.setTotalFiles(1);
        batch.setProcessedFiles(1);

        UploadItem item = new UploadItem(batch, "resume.txt");
        item.setStatus(UploadItemStatus.PROCESSING);
        item.setStoragePath("resume.txt");
        item.setQueueJobKey("lease-1");

        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(fileStorageService.exists("resume.txt")).thenReturn(true);
        when(fileStorageService.load("resume.txt")).thenReturn(new ByteArrayInputStream("resume-content".getBytes()));
        when(resumeProcessService.process(
                any(java.io.InputStream.class),
                eq("application/octet-stream"),
                eq("resume.txt"),
                eq(null),
                eq(jdId)))
                .thenThrow(new IllegalStateException("parse failed"));
        when(itemRepository.completeProcessingFailure(
                item.getId(),
                "lease-1",
                "parse failed",
                UploadItemStatus.FAILED,
                UploadItemStatus.PROCESSING))
                .thenReturn(1);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        UploadQueueWorkerService service = new UploadQueueWorkerService(
                itemRepository,
                batchRepository,
                fileStorageService,
                resumeProcessService,
                batchStreamService,
                jdbcTemplate,
                transactionManager,
                300_000L,
                100,
                5_000L);

        service.processClaimedItem(item.getId());

        verify(itemRepository).completeProcessingFailure(
                item.getId(),
                "lease-1",
                "parse failed",
                UploadItemStatus.FAILED,
                UploadItemStatus.PROCESSING);
        verify(batchRepository).refreshProgressFromItems(batchId);

        ArgumentCaptor<BatchStreamEvent> eventCaptor = ArgumentCaptor.forClass(BatchStreamEvent.class);
        verify(batchStreamService).publish(eq(batchId), eventCaptor.capture());

        BatchStreamEvent event = eventCaptor.getValue();
        assertEquals("FAILED", event.status());
        assertEquals("resume.txt", event.fileName());
        assertEquals("parse failed", event.errorMessage());
        assertNull(event.candidateId());
        assertEquals(1, event.totalFiles());
        assertEquals(1, event.processedFiles());
    }
}
