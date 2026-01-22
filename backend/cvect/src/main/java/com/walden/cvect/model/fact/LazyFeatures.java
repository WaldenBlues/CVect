package com.walden.cvect.model.fact;

public final class LazyFeatures {

    private final String text;

    private Boolean hasDigit;
    private Boolean hasTimePattern;
    private Boolean hasEmail;
    private Boolean hasUrl;
    private Boolean hasResponsibilityCue;
    private Boolean startsWithVerbLike;

    public LazyFeatures(String text) {
        this.text = text == null ? "" : text;
    }

    public boolean hasDigit() {
        if (hasDigit == null) {
            hasDigit = text.chars().anyMatch(Character::isDigit);
        }
        return hasDigit;
    }

    public boolean hasTimePattern() {
        if (hasTimePattern == null) {
            hasTimePattern = Regex.Chunk_TIME_PATTERN.matcher(text).find();
        }
        return hasTimePattern;
    }

    public boolean hasEmail() {
        if (hasEmail == null) {
            hasEmail = Regex.Chunk_EMAIL_PATTERN.matcher(text).find();
        }
        return hasEmail;
    }

    public boolean hasUrl() {
        if (hasUrl == null) {
            hasUrl = Regex.Chunk_URL_PATTERN.matcher(text).find();
        }
        return hasUrl;
    }

    public boolean hasResponsibilityCue() {
        if (hasResponsibilityCue == null) {
            hasResponsibilityCue = text.contains("负责")
                    || text.contains("完成")
                    || text.contains("设计")
                    || text.contains("实现")
                    || text.contains("搭建")
                    || text.contains("管理");
        }
        return hasResponsibilityCue;
    }

    public boolean startsWithVerbLike() {
        if (startsWithVerbLike == null) {
            startsWithVerbLike = text.startsWith("完成")
                    || text.startsWith("负责")
                    || text.startsWith("参与")
                    || text.startsWith("实现");
        }
        return startsWithVerbLike;
    }

    private Boolean hasHonorCue;

    public boolean hasHonorCue() {
        if (hasHonorCue == null) {
            // 感应：奖、赛、证书、名次、第一、称号、获...
            hasHonorCue = text.contains("奖")
                    || text.contains("赛")
                    || text.contains("名")
                    || text.contains("证")
                    || text.contains("称号")
                    || text.contains("获")
                    || text.contains("Top")
                    || text.contains("第一");
        }
        return hasHonorCue;
    }
}
