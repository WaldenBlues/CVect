package com.walden.cvect.web.controller.candidate;

import com.walden.cvect.web.stream.CandidateStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 候选人实时入库事件流
 */
@RestController
@RequestMapping("/api/candidates")
public class CandidateStreamController {

    private final CandidateStreamService streamService;

    public CandidateStreamController(CandidateStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamService.register();
    }
}
