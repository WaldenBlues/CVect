package com.walden.cvect.infra.embedding;

import org.junit.jupiter.api.DisplayName;
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
    "app.embedding.model-name=Qwen/Qwen2.5-Embedding-0.6B-Instruct",
    "app.embedding.device=cpu",
    "app.embedding.batch-size=32",
    "app.embedding.dimension=768",
    "app.embedding.max-input-length=8192"
})
@DisplayName("EmbeddingConfig 配置测试")
class EmbeddingConfigTest {

    @Autowired
    private EmbeddingConfig config;

    @Test
    @DisplayName("配置类应正确加载属性值")
    void should_load_config_values() {
        // Then
        assertEquals("Qwen/Qwen2.5-Embedding-0.6B-Instruct", config.getModelName());
        assertEquals("cpu", config.getDevice());
        assertEquals(32, config.getBatchSize());
        assertEquals(768, config.getDimension());
        assertEquals(8192, config.getMaxInputLength());
    }

    @Test
    @DisplayName("配置类应支持默认值")
    void should_have_default_values() {
        // Given: 使用默认配置创建新实例
        EmbeddingConfig newConfig = new EmbeddingConfig();

        // Then: 验证默认值设置
        assertEquals("Qwen/Qwen2.5-Embedding-0.6B-Instruct", newConfig.getModelName());
        assertEquals("cpu", newConfig.getDevice());
        assertEquals(32, newConfig.getBatchSize());
        assertEquals(768, newConfig.getDimension());
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
