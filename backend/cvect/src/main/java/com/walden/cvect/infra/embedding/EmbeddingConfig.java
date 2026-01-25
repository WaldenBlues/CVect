package com.walden.cvect.infra.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingConfig {

    private String modelName = "Qwen/Qwen2.5-Embedding-0.6B-Instruct";
    private String device = "cpu";
    private int batchSize = 32;
    private int dimension = 768;
    private int maxInputLength = 8192;

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public int getMaxInputLength() {
        return maxInputLength;
    }

    public void setMaxInputLength(int maxInputLength) {
        this.maxInputLength = maxInputLength;
    }
}
