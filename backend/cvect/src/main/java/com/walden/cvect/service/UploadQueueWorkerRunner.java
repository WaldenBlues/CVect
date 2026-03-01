package com.walden.cvect.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用自定义 TaskExecutor 启动 DB queue worker 轮询。
 * 不依赖 @Scheduled 线程池执行上传消费逻辑。
 */
@Component
@ConditionalOnProperty(name = "app.upload.worker.enabled", havingValue = "true", matchIfMissing = true)
public class UploadQueueWorkerRunner {

    private static final Logger log = LoggerFactory.getLogger(UploadQueueWorkerRunner.class);

    private final UploadQueueWorkerService workerService;
    private final TaskExecutor uploadWorkerExecutor;
    private final long initialDelayMs;
    private final long idleSleepMs;
    private final int consumerCount;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Set<Thread> runnerThreads = ConcurrentHashMap.newKeySet();

    public UploadQueueWorkerRunner(
            UploadQueueWorkerService workerService,
            @Qualifier("uploadWorkerExecutor") TaskExecutor uploadWorkerExecutor,
            @Value("${app.upload.worker.initial-delay-ms:3000}") long initialDelayMs,
            @Value("${app.upload.worker.idle-sleep-ms:${app.upload.worker.fixed-delay-ms:2000}}") long idleSleepMs,
            @Value("${app.upload.worker.consumer-count:4}") int consumerCount) {
        this.workerService = workerService;
        this.uploadWorkerExecutor = uploadWorkerExecutor;
        this.initialDelayMs = Math.max(0L, initialDelayMs);
        this.idleSleepMs = Math.max(1L, idleSleepMs);
        this.consumerCount = Math.max(1, consumerCount);
    }

    @PostConstruct
    void start() {
        if (started.compareAndSet(false, true)) {
            log.info("Starting upload queue workers: consumerCount={}, idleSleepMs={}", consumerCount, idleSleepMs);
            for (int i = 0; i < consumerCount; i++) {
                final int workerIndex = i;
                uploadWorkerExecutor.execute(() -> runLoop(workerIndex));
            }
        }
    }

    @PreDestroy
    void stop() {
        running.set(false);
        for (Thread thread : runnerThreads) {
            thread.interrupt();
        }
    }

    private void runLoop(int workerIndex) {
        Thread current = Thread.currentThread();
        runnerThreads.add(current);
        sleepQuietly(initialDelayMs + (workerIndex * 50L));
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int processed = workerService.consumeQueuedItems();
                if (processed > 0) {
                    continue;
                }
            } catch (Exception ex) {
                log.warn("Upload worker loop failed and will retry", ex);
            }
            sleepQuietly(idleSleepMs);
        }
        runnerThreads.remove(current);
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
