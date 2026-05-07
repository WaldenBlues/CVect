package com.walden.cvect.infra.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.util.Objects;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    FileStorageService localFileStorageService(StorageProperties properties) {
        return new LocalFileStorageService(properties.getLocalRoot());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    S3Client s3Client(StorageProperties properties) {
        return buildS3Client(properties);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
    S3Client minioClient(StorageProperties properties) {
        return buildS3Client(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    FileStorageService s3FileStorageService(S3Client s3Client, StorageProperties properties) {
        return new S3FileStorageService(s3Client, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
    FileStorageService minioFileStorageService(S3Client minioClient, StorageProperties properties) {
        return new S3FileStorageService(minioClient, properties);
    }

    private S3Client buildS3Client(StorageProperties properties) {
        String accessKey = requireConfiguredValue(properties.getAccessKey(), "app.storage.access-key");
        String secretKey = requireConfiguredValue(properties.getSecretKey(), "app.storage.secret-key");
        String region = requireConfiguredValue(properties.getRegion(), "app.storage.region");

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());

        String endpoint = Objects.requireNonNullElse(properties.getEndpoint(), "").trim();
        if (!endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private String requireConfiguredValue(String value, String propertyName) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        return normalized;
    }
}
