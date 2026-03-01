package com.walden.cvect.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class VectorIngestWorkerExecutorConfig {

    @Bean(name = "vectorIngestWorkerExecutor")
    public TaskExecutor vectorIngestWorkerExecutor(
            @Value("${app.vector.ingest.worker.executor.core-pool-size:1}") int corePoolSize,
            @Value("${app.vector.ingest.worker.executor.max-pool-size:2}") int maxPoolSize,
            @Value("${app.vector.ingest.worker.executor.queue-capacity:100}") int queueCapacity,
            @Value("${app.vector.ingest.worker.executor.thread-name-prefix:vector-ingest-worker-}") String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(corePoolSize, maxPoolSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
