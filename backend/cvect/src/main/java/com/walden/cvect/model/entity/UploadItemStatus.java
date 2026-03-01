package com.walden.cvect.model.entity;

import java.util.Locale;

public enum UploadItemStatus {
    QUEUED,
    PROCESSING,
    DONE,
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
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("PENDING".equals(upper) || "RETRYING".equals(upper)) {
            return QUEUED;
        }
        if ("SUCCEEDED".equals(upper)) {
            return DONE;
        }
        try {
            return UploadItemStatus.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
