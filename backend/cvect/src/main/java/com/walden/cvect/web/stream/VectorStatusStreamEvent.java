package com.walden.cvect.web.stream;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SSE 向量处理状态事件
 */
public record VectorStatusStreamEvent(
        UUID candidateId,
        UUID tenantId,
        UUID jdId,
        String status,
        boolean noVectorChunk,
        LocalDateTime updatedAt
) {
}
