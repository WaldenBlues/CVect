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
            @RequestParam(value = "contentType", required = false) String contentType) throws IOException {

        if (contentType == null || contentType.isBlank()) {
            contentType = file.getContentType();
        }

        var result = processService.process(file.getInputStream(), contentType);

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