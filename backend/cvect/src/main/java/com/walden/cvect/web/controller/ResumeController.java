package com.walden.cvect.web.controller;

import com.walden.cvect.service.ResumeProcessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final ResumeProcessService processService;

    public ResumeController(ResumeProcessService processService) {
        this.processService = processService;
    }

    /**
     * 解析简历并返回所有 Chunk
     */
    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "jdId", required = false) String jdId) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
        }
        if (jdId == null || jdId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "jdId is required"));
        }

        if (contentType == null || contentType.isBlank()) {
            contentType = file.getContentType();
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = DEFAULT_CONTENT_TYPE;
        }

        java.util.UUID jobId;
        try {
            jobId = java.util.UUID.fromString(jdId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "jdId is invalid"));
        }

        var result = processService.process(
                file.getInputStream(),
                contentType,
                file.getOriginalFilename(),
                file.getSize(),
                jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("candidateId", result.candidateId().toString());
        response.put("totalChunks", result.chunks().size());
        response.put("chunks", result.chunks());

        return ResponseEntity.ok(response);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }
}
