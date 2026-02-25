package com.walden.cvect.model.entity;

import java.util.Locale;

public enum UploadItemStatus {
    PENDING,
    QUEUED,
    PROCESSING,
    RETRYING,
    DONE,
    SUCCEEDED,
    DUPLICATE,
    FAILED;

    public static UploadItemStatus parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return UploadItemStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
