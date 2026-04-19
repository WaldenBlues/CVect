package com.walden.cvect.web.controller.upload;

import com.walden.cvect.service.upload.UploadApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 简历上传 API（单/多文件 + ZIP 批量）
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final UploadApplicationService uploadApplicationService;

    public UploadController(UploadApplicationService uploadApplicationService) {
        this.uploadApplicationService = uploadApplicationService;
    }

    @PostMapping("/resumes")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).RESUME_UPLOAD)")
    public ResponseEntity<UploadApplicationService.BatchUploadResponse> uploadResumes(
            @RequestParam("jdId") String jdId,
            @RequestParam("files") MultipartFile[] files) throws IOException {
        return ResponseEntity.ok(uploadApplicationService.uploadResumes(jdId, files));
    }

    @PostMapping("/zip")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).RESUME_UPLOAD)")
    public ResponseEntity<UploadApplicationService.ZipUploadResponse> uploadZip(
            @RequestParam("jdId") String jdId,
            @RequestParam("zipFile") MultipartFile zipFile) throws IOException {
        return ResponseEntity.ok(uploadApplicationService.uploadZip(jdId, zipFile));
    }
}
