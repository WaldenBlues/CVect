package com.walden.cvect.config;

import com.walden.cvect.infra.embedding.EmbeddingConfig;
import com.walden.cvect.infra.vector.VectorStoreConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 启动期维度守卫：启用向量检索时，embedding/vector 维度必须一致。
 */
@Component
public class VectorDimensionConsistencyValidator {

    private final EmbeddingConfig embeddingConfig;
    private final VectorStoreConfig vectorStoreConfig;

    public VectorDimensionConsistencyValidator(
            EmbeddingConfig embeddingConfig,
            VectorStoreConfig vectorStoreConfig) {
        this.embeddingConfig = embeddingConfig;
        this.vectorStoreConfig = vectorStoreConfig;
    }

    @PostConstruct
    void validate() {
        int embeddingDimension = embeddingConfig.getDimension();
        int vectorDimension = vectorStoreConfig.getDimension();

        if (embeddingDimension <= 0) {
            throw new IllegalStateException("app.embedding.dimension must be > 0");
        }
        if (vectorStoreConfig.isEnabled() && vectorDimension <= 0) {
            throw new IllegalStateException("app.vector.dimension must be > 0 when app.vector.enabled=true");
        }
        if (vectorStoreConfig.isEnabled() && embeddingDimension != vectorDimension) {
            throw new IllegalStateException(
                    "Dimension mismatch: app.embedding.dimension=" + embeddingDimension
                            + ", app.vector.dimension=" + vectorDimension
                            + ". Keep them identical (e.g. set CVECT_VECTOR_DIMENSION to CVECT_EMBEDDING_DIMENSION).");
        }
    }
}
