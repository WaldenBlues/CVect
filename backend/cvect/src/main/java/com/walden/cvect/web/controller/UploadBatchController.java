package com.walden.cvect.web.controller;

import com.walden.cvect.service.UploadBatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/uploads/batches")
public class UploadBatchController {

    private final UploadBatchService uploadBatchService;

    public UploadBatchController(UploadBatchService uploadBatchService) {
        this.uploadBatchService = uploadBatchService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<UploadBatchService.BatchOverview> getBatch(@PathVariable("id") UUID id) {
        return uploadBatchService.getBatchOverview(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<Page<UploadBatchService.UploadItemView>> getBatchItems(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        String normalizedStatus = status == null ? null : status.trim();

        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return uploadBatchService.getBatchItems(id, normalizedStatus, pageable)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/retry-failed")
    public ResponseEntity<UploadBatchService.RetryFailedResult> retryFailed(@PathVariable("id") UUID id) {
        return uploadBatchService.retryFailed(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
