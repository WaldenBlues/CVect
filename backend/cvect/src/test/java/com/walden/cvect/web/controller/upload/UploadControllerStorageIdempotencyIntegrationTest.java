package com.walden.cvect.web.controller.upload;

import com.jayway.jsonpath.JsonPath;
import com.walden.cvect.config.PostgresIntegrationTestBase;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import com.walden.cvect.service.upload.queue.UploadQueueWorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=true",
        "app.upload.worker.initial-delay-ms=600000",
        "app.upload.worker.fixed-delay-ms=600000",
        "app.upload.max-inflight-items=1"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class UploadControllerStorageIdempotencyIntegrationTest extends PostgresIntegrationTestBase {

    private static final Path STORAGE_DIR = Paths.get("storage");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobDescriptionJpaRepository jobDescriptionRepository;

    @Autowired
    private UploadItemJpaRepository itemRepository;

    @Autowired
    private UploadBatchJpaRepository batchRepository;

    @Autowired
    private UploadQueueWorkerService workerService;

    @BeforeEach
    void cleanQueueState() {
        itemRepository.deleteAll();
        batchRepository.deleteAll();
    }

    @Test
    @DisplayName("same resume content uploaded twice should keep only one storage blob")
    void sameContentShouldKeepSingleBlob() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Storage Idempotency JD", "desc"));
        byte[] content = ("idempotency-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String hash = sha256Hex(content);

        MockMultipartFile file1 = new MockMultipartFile("files", "same-1.txt", "text/plain", content);
        MockMultipartFile file2 = new MockMultipartFile("files", "same-2.txt", "text/plain", content);

        MvcResult firstUpload = mockMvc.perform(multipart("/api/uploads/resumes")
                        .file(file1)
                        .param("jdId", jd.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].status").value("QUEUED"))
                .andReturn();

        UUID firstBatchId = UUID.fromString(JsonPath.read(
                firstUpload.getResponse().getContentAsString(),
                "$.batchId"));
        UUID firstItemId = itemRepository.findByBatch_Id(firstBatchId, PageRequest.of(0, 1))
                .getContent()
                .get(0)
                .getId();
        workerService.consumeQueuedItems();
        assertEquals(UploadItemStatus.DONE, itemRepository.findById(firstItemId).orElseThrow().getStatus());

        MvcResult secondUpload = mockMvc.perform(multipart("/api/uploads/resumes")
                        .file(file2)
                        .param("jdId", jd.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].status").value("QUEUED"))
                .andReturn();

        UUID secondBatchId = UUID.fromString(JsonPath.read(
                secondUpload.getResponse().getContentAsString(),
                "$.batchId"));
        UUID secondItemId = itemRepository.findByBatch_Id(secondBatchId, PageRequest.of(0, 1))
                .getContent()
                .get(0)
                .getId();
        workerService.consumeQueuedItems();
        assertEquals(UploadItemStatus.DUPLICATE, itemRepository.findById(secondItemId).orElseThrow().getStatus());

        Path hashedFilePath = STORAGE_DIR.resolve(hash);
        assertTrue(Files.exists(hashedFilePath), "expected hashed file to exist in storage");

        try (Stream<Path> paths = Files.list(STORAGE_DIR)) {
            long count = paths.filter(path -> path.getFileName().toString().equals(hash)).count();
            assertEquals(1L, count, "same content should map to one storage file");
        }
    }

    @Test
    @DisplayName("upload resumes should return 429 when inflight queue is full")
    void uploadShouldReturn429WhenInflightQueueIsFull() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Queue Guard JD", "desc"));
        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 1));
        UploadItem queued = new UploadItem(batch, "queued.txt");
        queued.setStatus(UploadItemStatus.QUEUED);
        itemRepository.save(queued);

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "new.txt",
                "text/plain",
                "new-content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/uploads/resumes")
                        .file(file)
                        .param("jdId", jd.getId().toString()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("empty file should be rejected without queueing")
    void emptyFileShouldBeRejectedWithoutQueueing() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Empty File JD", "desc"));
        MockMultipartFile empty = new MockMultipartFile(
                "files",
                "empty.txt",
                "text/plain",
                new byte[0]);

        MvcResult result = mockMvc.perform(multipart("/api/uploads/resumes")
                        .file(empty)
                        .param("jdId", jd.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].status").value("FAILED"))
                .andExpect(jsonPath("$.files[0].errorMessage").value("Empty file is not allowed"))
                .andReturn();

        UUID batchId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.batchId"));
        UploadItem item = itemRepository.findByBatch_Id(batchId, PageRequest.of(0, 1)).getContent().get(0);
        assertEquals(UploadItemStatus.FAILED, item.getStatus());
    }

    @Test
    @DisplayName("empty zip upload should return 400 and create no batch")
    void emptyZipShouldReturn400AndCreateNoBatch() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Empty Zip JD", "desc"));
        long before = batchRepository.count();

        MockMultipartFile emptyZip = new MockMultipartFile(
                "zipFile",
                "empty.zip",
                "application/zip",
                new byte[0]);

        mockMvc.perform(multipart("/api/uploads/zip")
                        .file(emptyZip)
                        .param("jdId", jd.getId().toString()))
                .andExpect(status().isBadRequest());

        assertEquals(before, batchRepository.count(), "empty zip should not create upload batch");
    }

    @Test
    @DisplayName("zip with unsupported entry should mark item failed")
    void zipWithUnsupportedEntryShouldFailItem() throws Exception {
        JobDescription jd = jobDescriptionRepository.save(new JobDescription("Zip Unsupported Entry JD", "desc"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("bad.exe"));
            zos.write("dummy".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile zip = new MockMultipartFile(
                "zipFile",
                "files.zip",
                "application/zip",
                baos.toByteArray());

        MvcResult result = mockMvc.perform(multipart("/api/uploads/zip")
                        .file(zip)
                        .param("jdId", jd.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        UUID batchId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.batchId"));
        UploadItem item = itemRepository.findByBatch_Id(batchId, PageRequest.of(0, 1)).getContent().get(0);
        assertEquals(UploadItemStatus.FAILED, item.getStatus());
        assertEquals("Unsupported file type", item.getErrorMessage());
    }

    private String sha256Hex(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
