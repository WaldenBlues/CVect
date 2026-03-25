package com.walden.cvect.config;

import org.testcontainers.utility.DockerImageName;

public final class TestContainerImages {

    private static final String DEFAULT_POSTGRES_IMAGE =
            "m.daocloud.io/docker.io/pgvector/pgvector:pg17";

    private TestContainerImages() {
    }

    public static DockerImageName postgresPgvector() {
        String image = firstNonBlank(
                System.getProperty("cvect.testcontainers.postgres.image"),
                System.getenv("CVECT_TESTCONTAINERS_POSTGRES_IMAGE"),
                System.getenv("CVECT_POSTGRES_IMAGE"),
                DEFAULT_POSTGRES_IMAGE);
        return DockerImageName.parse(image).asCompatibleSubstituteFor("postgres");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
