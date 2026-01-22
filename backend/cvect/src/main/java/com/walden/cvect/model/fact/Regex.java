package com.walden.cvect.model.fact;

import java.util.regex.Pattern;

public final class Regex {
    public static final Pattern Chunk_TIME_PATTERN = Pattern.compile("\\d{4}[./-]\\d{2}");

    public static final Pattern Chunk_EMAIL_PATTERN = Pattern.compile("[\\w.-]+@[\\w.-]+");

    public static final Pattern Chunk_URL_PATTERN = Pattern.compile("https?://\\S+");

    public static final Pattern EMAIL_STRICT = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    public static final Pattern PHONE_STRICT = Pattern.compile("(\\+86)?1[3-9]\\d{9}");

    public static final Pattern URL_STRICT = Pattern.compile("https?://[\\w.-]+(?:/[\\w./-]*)?");

    public static final Pattern TIME_NOISE = Pattern.compile("\\d{4}[./-]\\d{2}(\\s*-\\s*\\d{4}[./-]\\d{2})?");
}
