package com.walden.cvect.actuator;

import com.walden.cvect.web.sse.BatchStreamService;
import com.walden.cvect.web.stream.CandidateStreamService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Endpoint(id = "streamperformance")
public class StreamPerformanceEndpoint {

    private final CandidateStreamService candidateStreamService;
    private final BatchStreamService batchStreamService;

    public StreamPerformanceEndpoint(
            CandidateStreamService candidateStreamService,
            BatchStreamService batchStreamService) {
        this.candidateStreamService = candidateStreamService;
        this.batchStreamService = batchStreamService;
    }

   @ReadOperation
public Map<String, Object> performance() {
    int candidateCount = candidateStreamService.activeEmitterCount();
    int batchCount = batchStreamService.activeEmitterCount();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("candidateEmitterCount", candidateCount);
    result.put("batchEmitterCount", batchCount);
    result.put("totalEmitterCount", candidateCount + batchCount);
    return result;
}
}
