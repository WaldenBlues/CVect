package com.walden.cvect.actuator;

import com.walden.cvect.web.sse.BatchStreamService;
import com.walden.cvect.web.stream.CandidateStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StreamPerformanceEndpoint tests")
class StreamPerformanceEndpointTest {

    @Mock
    private CandidateStreamService candidateStreamService;

    @Mock
    private BatchStreamService batchStreamService;

    @Test
    @DisplayName("shouldCalculateEmitterCounts")
    void shouldCalculateEmitterCounts() {
        when(candidateStreamService.activeEmitterCount()).thenReturn(3);
        when(batchStreamService.activeEmitterCount()).thenReturn(2);

        StreamPerformanceEndpoint endpoint = new StreamPerformanceEndpoint(candidateStreamService, batchStreamService);

        Map<String, Object> performance = endpoint.performance();

        assertThat(performance)
                .containsEntry("candidateEmitterCount", 3)
                .containsEntry("batchEmitterCount", 2)
                .containsEntry("totalEmitterCount", 5);
    }
}
