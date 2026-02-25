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
    private final long fixedDelayMs;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Thread runnerThread;

    public UploadQueueWorkerRunner(
            UploadQueueWorkerService workerService,
            @Qualifier("uploadWorkerExecutor") TaskExecutor uploadWorkerExecutor,
            @Value("${app.upload.worker.initial-delay-ms:3000}") long initialDelayMs,
            @Value("${app.upload.worker.fixed-delay-ms:2000}") long fixedDelayMs) {
        this.workerService = workerService;
        this.uploadWorkerExecutor = uploadWorkerExecutor;
        this.initialDelayMs = Math.max(0L, initialDelayMs);
        this.fixedDelayMs = Math.max(1L, fixedDelayMs);
    }

    @PostConstruct
    void start() {
        if (started.compareAndSet(false, true)) {
            uploadWorkerExecutor.execute(this::runLoop);
        }
    }

    @PreDestroy
    void stop() {
        running.set(false);
        Thread thread = runnerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop() {
        runnerThread = Thread.currentThread();
        sleepQuietly(initialDelayMs);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                workerService.consumeQueuedItems();
            } catch (Exception ex) {
                log.warn("Upload worker loop failed and will retry", ex);
            }
            sleepQuietly(fixedDelayMs);
        }
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
