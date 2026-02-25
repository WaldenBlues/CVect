package com.walden.cvect.web.controller;

import com.walden.cvect.web.sse.BatchStreamService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 批次级 SSE 订阅
 */
@RestController
@RequestMapping("/api/sse/batches")
public class BatchSseController {

    private final BatchStreamService batchStreamService;

    public BatchSseController(BatchStreamService batchStreamService) {
        this.batchStreamService = batchStreamService;
    }

    @GetMapping("/{batchId}")
    public SseEmitter stream(@PathVariable UUID batchId) {
        return batchStreamService.subscribe(batchId);
    }
}
