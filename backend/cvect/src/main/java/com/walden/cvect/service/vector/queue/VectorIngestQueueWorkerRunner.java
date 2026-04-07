package com.walden.cvect.service.vector.queue;

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

@Component
@ConditionalOnProperty(name = "app.vector.ingest.worker.enabled", havingValue = "true", matchIfMissing = true)
public class VectorIngestQueueWorkerRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestQueueWorkerRunner.class);

    private final VectorIngestQueueWorkerService workerService;
    private final TaskExecutor vectorIngestWorkerExecutor;
    private final long initialDelayMs;
    private final long idleSleepMs;
    private final int consumerCount;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Set<Thread> runnerThreads = ConcurrentHashMap.newKeySet();

    public VectorIngestQueueWorkerRunner(
            VectorIngestQueueWorkerService workerService,
            @Qualifier("vectorIngestWorkerExecutor") TaskExecutor vectorIngestWorkerExecutor,
            @Value("${app.vector.ingest.worker.initial-delay-ms:3000}") long initialDelayMs,
            @Value("${app.vector.ingest.worker.idle-sleep-ms:500}") long idleSleepMs,
            @Value("${app.vector.ingest.worker.consumer-count:1}") int consumerCount) {
        this.workerService = workerService;
        this.vectorIngestWorkerExecutor = vectorIngestWorkerExecutor;
        this.initialDelayMs = Math.max(0L, initialDelayMs);
        this.idleSleepMs = Math.max(1L, idleSleepMs);
        this.consumerCount = Math.max(1, consumerCount);
    }

    @PostConstruct
    void start() {
        if (started.compareAndSet(false, true)) {
            log.info("Starting vector ingest workers: consumerCount={}, idleSleepMs={}", consumerCount, idleSleepMs);
            for (int i = 0; i < consumerCount; i++) {
                final int workerIndex = i;
                vectorIngestWorkerExecutor.execute(() -> runLoop(workerIndex));
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
                int processed = workerService.consumePendingTasks();
                if (processed > 0) {
                    continue;
                }
            } catch (Exception ex) {
                log.warn("Vector ingest worker loop failed and will retry", ex);
            }
            sleepQuietly(idleSleepMs);
        }
        runnerThreads.remove(current);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
