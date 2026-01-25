package com.walden.cvect.infra.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.vector")
public class VectorStoreConfig {

    private String tableName = "resume_chunks";
    private String indexType = "hnsw";
    private String metric = "cosine";
    private int efConstruction = 64;
    private int m = 16;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public int getEfConstruction() {
        return efConstruction;
    }

    public void setEfConstruction(int efConstruction) {
        this.efConstruction = efConstruction;
    }

    public int getM() {
        return m;
    }

    public void setM(int m) {
        this.m = m;
    }
}
