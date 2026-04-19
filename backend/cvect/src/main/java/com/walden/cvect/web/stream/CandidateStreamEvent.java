package com.walden.cvect.web.stream;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SSE 推送载体
 */
public record CandidateStreamEvent(
        UUID candidateId,
        UUID tenantId,
        UUID jdId,
        String status,
        String recruitmentStatus,
        String name,
        String sourceFileName,
        String contentType,
        Long fileSizeBytes,
        Integer parsedCharCount,
        Boolean truncated,
        LocalDateTime createdAt,
        List<String> emails,
        List<String> phones,
        List<String> educations,
        List<String> honors,
        List<String> links
) {
}
