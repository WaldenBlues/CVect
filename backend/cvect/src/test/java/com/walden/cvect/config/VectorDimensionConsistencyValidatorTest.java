package com.walden.cvect.config;

import com.walden.cvect.infra.embedding.EmbeddingConfig;
import com.walden.cvect.infra.vector.VectorStoreConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class VectorDimensionConsistencyValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(
                    EmbeddingConfig.class,
                    VectorStoreConfig.class,
                    VectorDimensionConsistencyValidator.class);

    @Test
    void should_start_when_vector_disabled_even_if_dimensions_differ() {
        contextRunner
                .withPropertyValues(
                        "app.embedding.dimension=1024",
                        "app.vector.enabled=false",
                        "app.vector.dimension=768")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void should_fail_when_vector_enabled_and_dimensions_differ() {
        contextRunner
                .withPropertyValues(
                        "app.embedding.dimension=1024",
                        "app.vector.enabled=true",
                        "app.vector.dimension=768")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Dimension mismatch: app.embedding.dimension=1024, app.vector.dimension=768. Keep them identical (e.g. set CVECT_VECTOR_DIMENSION to CVECT_EMBEDDING_DIMENSION).");
                });
    }

    @Test
    void should_start_when_vector_enabled_and_dimensions_match() {
        contextRunner
                .withPropertyValues(
                        "app.embedding.dimension=1024",
                        "app.vector.enabled=true",
                        "app.vector.dimension=1024")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
