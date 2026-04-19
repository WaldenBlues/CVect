package com.walden.cvect.service.matching;

import com.walden.cvect.web.controller.search.SearchController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

public final class SearchCacheKeys {

    private SearchCacheKeys() {
    }

    public static String queryEmbedding(String jobDescription) {
        return "jd:" + sha256(normalizeText(jobDescription));
    }

    public static String searchRequest(SearchController.SearchRequest request) {
        return searchRequest(request, null);
    }

    public static String searchRequest(SearchController.SearchRequest request, UUID tenantId) {
        if (request == null) {
            return "search:%s:null".formatted(tenantId);
        }
        SearchWeightNormalizer.Weights weights = SearchWeightNormalizer.resolve(request);
        return "search:%s:%s:%d:%s:%s:%s:%s:%s".formatted(
                tenantId == null ? "default" : tenantId,
                sha256(normalizeText(request.jobDescription())),
                request.topK(),
                request.filterByExperience(),
                request.filterBySkill(),
                normalizeFloat(weights.experienceWeight()),
                normalizeFloat(weights.skillWeight()),
                request.onlyVectorReadyCandidates());
    }

    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeFloat(Float value) {
        if (value == null || !Float.isFinite(value)) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
