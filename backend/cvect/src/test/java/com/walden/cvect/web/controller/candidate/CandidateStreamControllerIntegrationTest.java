package com.walden.cvect.web.controller.candidate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@AutoConfigureMockMvc
@Tag("integration")
@Tag("api")
class CandidateStreamControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/candidates/stream should emit an initial ping immediately")
    void streamShouldEmitInitialPingImmediately() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/candidates/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:ping");
        assertThat(body).contains("data:ok");
    }
}
