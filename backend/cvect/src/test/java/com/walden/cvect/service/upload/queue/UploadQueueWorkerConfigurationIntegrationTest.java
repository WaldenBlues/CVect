package com.walden.cvect.service.upload.queue;

import com.walden.cvect.config.PostgresIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@DisplayName("UploadQueueWorker configuration integration tests")
class UploadQueueWorkerConfigurationIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired(required = false)
    private UploadQueueWorkerService workerService;

    @Autowired(required = false)
    private UploadQueueWorkerRunner workerRunner;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("upload worker beans should stay disabled when app.upload.worker.enabled=false")
    void uploadWorkerBeansShouldStayDisabledWhenPropertyIsFalse() {
        assertNull(workerService);
        assertNull(workerRunner);
        assertEquals(0, applicationContext.getBeanNamesForType(UploadQueueWorkerService.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(UploadQueueWorkerRunner.class).length);
    }
}
