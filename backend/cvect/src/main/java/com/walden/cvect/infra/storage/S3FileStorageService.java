package com.walden.cvect.infra.storage;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    public S3FileStorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucket() {
        if (!properties.isAutoCreateBucket()) {
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket()).build());
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                createBucket();
                return;
            }
            throw e;
        }
    }

    @Override
    public String save(String storageKey, InputStream inputStream) throws IOException {
        String normalizedStorageKey = normalizeStorageKey(storageKey);
        byte[] bytes;
        try (InputStream input = inputStream) {
            bytes = input.readAllBytes();
        }
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket())
                        .key(normalizedStorageKey)
                        .build(),
                RequestBody.fromBytes(bytes));
        return normalizedStorageKey;
    }

    @Override
    public InputStream load(String storageKey) {
        String normalizedStorageKey = normalizeStorageKey(storageKey);
        ResponseInputStream<?> inputStream = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket())
                        .key(normalizedStorageKey)
                        .build());
        return inputStream;
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket())
                    .key(normalizeStorageKey(storageKey))
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket())
                .key(normalizeStorageKey(storageKey))
                .build());
    }

    private void createBucket() {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket()).build());
    }

    private String bucket() {
        String bucket = Objects.requireNonNullElse(properties.getBucket(), "").trim();
        if (bucket.isEmpty()) {
            throw new IllegalArgumentException("app.storage.bucket must not be blank");
        }
        return bucket;
    }

    private String normalizeStorageKey(String storageKey) {
        String normalized = Objects.requireNonNullElse(storageKey, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        return normalized;
    }
}
