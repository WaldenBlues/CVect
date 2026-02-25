package com.walden.cvect.web.controller;

import com.jayway.jsonpath.JsonPath;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import com.walden.cvect.service.UploadQueueWorkerService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=true",
        "app.upload.worker.initial-delay-ms=600000",
        "app.upload.worker.fixed-delay-ms=600000",
        "spring.datasource.url=jdbc:h2:mem:upload_storage_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class UploadControllerStorageIdempotencyIntegrationTest {

    private static final Path STORAGE_DIR = Paths.get("storage");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobDescriptionJpaRepository jobDescriptionRepository;

    @Autowired
    private UploadItemJpaRepository itemRepository;

    @Autowired
    private UploadQueueWorkerService workerService;

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

    private String sha256Hex(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
