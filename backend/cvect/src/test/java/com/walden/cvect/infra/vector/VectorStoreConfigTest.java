package com.walden.cvect.infra.vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorStoreConfig 配置测试
 *
 * 测试原则：遵循完整流水线测试模式
 * PDF -> parser -> normalizer -> chunker -> fact extraction -> vector storage
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.vector.table-name=resume_chunks",
    "app.vector.index-type=hnsw",
    "app.vector.metric=cosine",
    "app.vector.ef-construction=64",
    "app.vector.m=16"
})
@DisplayName("VectorStoreConfig 配置测试")
class VectorStoreConfigTest {

    @Autowired
    private VectorStoreConfig config;

    @Test
    @DisplayName("配置类应正确加载属性值")
    void should_load_config_values() {
        // Then
        assertEquals("resume_chunks", config.getTableName());
        assertEquals("hnsw", config.getIndexType());
        assertEquals("cosine", config.getMetric());
        assertEquals(64, config.getEfConstruction());
        assertEquals(16, config.getM());
    }

    @Test
    @DisplayName("配置类应支持默认值")
    void should_have_default_values() {
        // Given: 使用默认配置创建新实例
        VectorStoreConfig newConfig = new VectorStoreConfig();

        // Then: 验证默认值设置
        assertEquals("resume_chunks", newConfig.getTableName());
        assertEquals("hnsw", newConfig.getIndexType());
        assertEquals("cosine", newConfig.getMetric());
    }

    @Test
    @DisplayName("配置类应支持属性修改")
    void should_support_property_modification() {
        // When
        config.setTableName("new_table");
        config.setIndexType("ivfflat");
        config.setMetric("l2");

        // Then
        assertEquals("new_table", config.getTableName());
        assertEquals("ivfflat", config.getIndexType());
        assertEquals("l2", config.getMetric());
    }
}
