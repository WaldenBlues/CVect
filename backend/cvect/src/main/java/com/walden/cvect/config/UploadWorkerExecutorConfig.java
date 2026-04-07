package com.walden.cvect.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskDecorator;

@Configuration
public class UploadWorkerExecutorConfig {

    @Bean(name = "uploadWorkerExecutor")
    public TaskExecutor uploadWorkerExecutor(
            @Value("${app.upload.worker.executor.core-pool-size:1}") int corePoolSize,
            @Value("${app.upload.worker.executor.max-pool-size:2}") int maxPoolSize,
            @Value("${app.upload.worker.executor.queue-capacity:100}") int queueCapacity,
            @Value("${app.upload.worker.executor.thread-name-prefix:upload-worker-}") String threadNamePrefix,
            TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(corePoolSize, maxPoolSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
