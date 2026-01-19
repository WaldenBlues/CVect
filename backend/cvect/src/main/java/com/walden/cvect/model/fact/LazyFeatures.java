package com.walden.cvect.model.fact;

import java.util.regex.Pattern;

public final class LazyFeatures {

    private final String text;

    private Boolean hasDigit;
    private Boolean hasTimePattern;
    private Boolean hasEmail;
    private Boolean hasUrl;
    private Boolean hasResponsibilityCue;
    private Boolean startsWithVerbLike;

    private static final Pattern TIME_PATTERN = Pattern.compile("\\d{4}[./-]\\d{2}");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.-]+@[\\w.-]+");

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

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
            hasTimePattern = TIME_PATTERN.matcher(text).find();
        }
        return hasTimePattern;
    }

    public boolean hasEmail() {
        if (hasEmail == null) {
            hasEmail = EMAIL_PATTERN.matcher(text).find();
        }
        return hasEmail;
    }

    public boolean hasUrl() {
        if (hasUrl == null) {
            hasUrl = URL_PATTERN.matcher(text).find();
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
}
