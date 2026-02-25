package com.walden.cvect.web.sse;

import java.time.LocalDateTime;
import java.util.UUID;

public record BatchStreamEvent(
        UUID batchId,
        String status,
        int totalFiles,
        int processedFiles,
        String fileName,
        UUID candidateId,
        String errorMessage,
        LocalDateTime timestamp
) {}
