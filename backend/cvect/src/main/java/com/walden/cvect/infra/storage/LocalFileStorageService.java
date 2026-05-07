package com.walden.cvect.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class LocalFileStorageService implements FileStorageService {

    private final Path rootDirectory;

    public LocalFileStorageService(String rootDirectory) {
        String normalizedRoot = Objects.requireNonNullElse(rootDirectory, "").trim();
        if (normalizedRoot.isEmpty()) {
            throw new IllegalArgumentException("app.storage.local-root must not be blank");
        }
        this.rootDirectory = Paths.get(normalizedRoot).toAbsolutePath().normalize();
    }

    @Override
    public String save(String storageKey, InputStream inputStream) throws IOException {
        Path target = resolveStoragePath(storageKey);
        Files.createDirectories(target.getParent());
        try (InputStream input = inputStream) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return normalizeStorageKey(storageKey);
    }

    @Override
    public InputStream load(String storageKey) throws IOException {
        return Files.newInputStream(resolveStoragePath(storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolveStoragePath(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveStoragePath(storageKey));
    }

    private Path resolveStoragePath(String storageKey) {
        String normalizedStorageKey = normalizeStorageKey(storageKey);
        Path relativePath = Paths.get(normalizedStorageKey).normalize();
        Path resolvedPath = rootDirectory.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Storage key resolves outside local root: " + storageKey);
        }
        return resolvedPath;
    }

    private String normalizeStorageKey(String storageKey) {
        String normalized = Objects.requireNonNullElse(storageKey, "")
                .trim()
                .replace('\\', '/');
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Absolute storage key is not allowed: " + storageKey);
        }
        Path normalizedPath = Paths.get(normalized).normalize();
        if (normalizedPath.isAbsolute() || normalizedPath.startsWith("..")) {
            throw new IllegalArgumentException("Storage key traversal is not allowed: " + storageKey);
        }
        String normalizedKey = normalizedPath.toString().replace('\\', '/');
        if (normalizedKey.isBlank() || ".".equals(normalizedKey)) {
            throw new IllegalArgumentException("Storage key must not resolve to current directory");
        }
        return normalizedKey;
    }
}
