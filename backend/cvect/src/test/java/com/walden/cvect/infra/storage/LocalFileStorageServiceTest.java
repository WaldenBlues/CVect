package com.walden.cvect.infra.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LocalFileStorageService tests")
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("save load exists delete should work under local root")
    void saveLoadExistsDeleteShouldWork() throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        byte[] payload = "resume-content".getBytes(StandardCharsets.UTF_8);

        String storageKey = service.save("uploads/test-resume.txt", new ByteArrayInputStream(payload));

        assertTrue(service.exists(storageKey));
        assertTrue(Files.exists(tempDir.resolve("uploads/test-resume.txt")));
        try (var input = service.load(storageKey)) {
            assertArrayEquals(payload, input.readAllBytes());
        }

        service.delete(storageKey);

        assertFalse(service.exists(storageKey));
    }

    @Test
    @DisplayName("path traversal should be rejected")
    void pathTraversalShouldBeRejected() {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());

        assertThrows(IllegalArgumentException.class,
                () -> service.save("../escape.txt", new ByteArrayInputStream(new byte[] {1})));
        assertThrows(IllegalArgumentException.class, () -> service.exists("../escape.txt"));
        assertThrows(IllegalArgumentException.class, () -> service.load("../escape.txt"));
        assertThrows(IllegalArgumentException.class, () -> service.delete("../escape.txt"));
    }
}
