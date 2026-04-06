package com.walden.cvect.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
public abstract class PostgresIntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            TestContainerImages.postgresPgvector())
            .withDatabaseName("cvect_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }

        if (dockerAvailable) {
            if (!POSTGRES.isRunning()) {
                POSTGRES.start();
            }
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.locations", () -> "classpath:db/migration");
            registry.add("app.vector.enabled", () -> "true");
            registry.add("app.vector.dimension", () -> "1024");
            registry.add("app.embedding.dimension", () -> "1024");
            registry.add("app.match-scores.enabled", () -> "false");
            return;
        }

        // Fallback for local environments without Docker: keep context bootable.
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:cvect-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.vector.enabled", () -> "false");
        registry.add("app.vector.dimension", () -> "1024");
        registry.add("app.embedding.dimension", () -> "1024");
        registry.add("app.match-scores.enabled", () -> "false");
    }
}
