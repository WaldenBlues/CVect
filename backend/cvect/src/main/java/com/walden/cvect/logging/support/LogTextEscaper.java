package com.walden.cvect.logging.support;

final class LogTextEscaper {

    private LogTextEscaper() {
    }

    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (Character.isISOControl(ch)) {
                        sb.append(unicodeEscape(ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String unicodeEscape(char ch) {
        String hex = Integer.toHexString(ch);
        return "\\u" + "0000".substring(hex.length()) + hex;
    }
}
