package com.walden.cvect.service.upload.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadQueueWorkerRunner unit tests")
class UploadQueueWorkerRunnerTest {

    @Mock
    private UploadQueueWorkerService workerService;

    @Test
    @DisplayName("stop should let queued workers exit before initial delay when shutdown wins the race")
    void stopShouldLetQueuedWorkersExitBeforeInitialDelayWhenShutdownWinsRace() throws Exception {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        UploadQueueWorkerRunner runner = new UploadQueueWorkerRunner(
                workerService,
                taskExecutor,
                10_000L,
                10_000L,
                1);

        runner.start();
        runner.stop();

        assertEquals(1, taskExecutor.tasks.size());
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> future = executorService.submit(taskExecutor.tasks.get(0));
        try {
            assertDoesNotThrow(() -> future.get(500, TimeUnit.MILLISECONDS));
        } finally {
            future.cancel(true);
            executorService.shutdownNow();
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        }

        verifyNoInteractions(workerService);
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }
    }
}
