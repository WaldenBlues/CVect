package com.walden.cvect.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadWorkerExecutorConfigTest {

    private final UploadWorkerExecutorConfig config = new UploadWorkerExecutorConfig();
    private final TaskDecorator noopTaskDecorator = task -> task;

    @Test
    void normalizesInvalidPoolSizes() {
        ThreadPoolTaskExecutor executor = executor(0, 0);

        assertEquals(1, executor.getCorePoolSize());
        assertEquals(1, executor.getMaxPoolSize());

        executor.shutdown();
    }

    @Test
    void preservesValidPoolSizes() {
        ThreadPoolTaskExecutor executor = executor(2, 4);

        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());

        executor.shutdown();
    }

    private ThreadPoolTaskExecutor executor(int corePoolSize, int maxPoolSize) {
        return (ThreadPoolTaskExecutor) config.uploadWorkerExecutor(
                corePoolSize,
                maxPoolSize,
                10,
                "upload-worker-",
                noopTaskDecorator);
    }
}
