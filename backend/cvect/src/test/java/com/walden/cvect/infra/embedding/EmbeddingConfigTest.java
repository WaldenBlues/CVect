package com.walden.cvect.infra.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingConfig 配置测试
 *
 * 测试原则：遵循完整流水线测试模式
 * PDF -> parser -> normalizer -> chunker -> fact extraction -> embedding
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.embedding.model-name=test-embedding-model",
    "app.embedding.service-url=http://localhost:9001/embed",
    "app.embedding.device=cuda",
    "app.embedding.batch-size=8",
    "app.embedding.dimension=1024",
    "app.embedding.max-input-length=4096",
    "app.embedding.timeout-seconds=15",
    "app.vector.enabled=false"
})
@Tag("integration")
@DisplayName("EmbeddingConfig 配置测试")
class EmbeddingConfigTest {

    @Autowired
    private EmbeddingConfig config;

    @Test
    @DisplayName("配置类应正确加载覆盖属性值")
    void should_load_config_values() {
        // Then
        assertEquals("test-embedding-model", config.getModelName());
        assertEquals("http://localhost:9001/embed", config.getServiceUrl());
        assertEquals("cuda", config.getDevice());
        assertEquals(8, config.getBatchSize());
        assertEquals(1024, config.getDimension());
        assertEquals(4096, config.getMaxInputLength());
        assertEquals(15, config.getTimeoutSeconds());
    }

    @Test
    @DisplayName("配置类应支持默认值")
    void should_have_default_values() {
        // Given: 使用默认配置创建新实例
        EmbeddingConfig newConfig = new EmbeddingConfig();

        // Then: 验证默认值设置
        assertEquals("Qwen/Qwen3-Embedding-0.6B", newConfig.getModelName());
        assertEquals("http://localhost:8001/embed", newConfig.getServiceUrl());
        assertEquals("cpu", newConfig.getDevice());
        assertEquals(16, newConfig.getBatchSize());
        assertEquals(1024, newConfig.getDimension());
        assertEquals(8192, newConfig.getMaxInputLength());
        assertEquals(60, newConfig.getTimeoutSeconds());
    }

    @Test
    @DisplayName("配置类应支持属性修改")
    void should_support_property_modification() {
        // Given: 创建独立实例，避免测试间污染
        EmbeddingConfig testConfig = new EmbeddingConfig();

        // When
        testConfig.setModelName("new-model");
        testConfig.setDevice("cuda");
        testConfig.setBatchSize(64);

        // Then
        assertEquals("new-model", testConfig.getModelName());
        assertEquals("cuda", testConfig.getDevice());
        assertEquals(64, testConfig.getBatchSize());
    }
}
