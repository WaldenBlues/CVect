package com.walden.cvect.service;

import java.util.UUID;

public final class UploadQueueJobKeyGenerator {

    private UploadQueueJobKeyGenerator() {
    }

    public static String nextKey(UUID itemId) {
        String prefix = itemId == null ? "upload-item" : itemId.toString();
        return prefix + ":" + UUID.randomUUID();
    }
}
