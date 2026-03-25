package com.walden.cvect.config;

import java.util.Objects;
import java.util.Random;

/**
 * 为测试提供稳定、可复现的 embedding 数据，避免依赖外部 HTTP 服务。
 */
public final class TestEmbeddings {

    public static final int DEFAULT_DIMENSION = 1024;

    private TestEmbeddings() {
    }

    public static float[] forText(String text) {
        return forText(text, DEFAULT_DIMENSION);
    }

    public static float[] forText(String text, int dimension) {
        Random random = new Random(Objects.requireNonNullElse(text, "").hashCode());
        float[] embedding = new float[dimension];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat();
        }
        return embedding;
    }
}
