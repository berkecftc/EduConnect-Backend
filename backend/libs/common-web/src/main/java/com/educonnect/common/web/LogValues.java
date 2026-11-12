package com.educonnect.common.web;

public final class LogValues {

    private LogValues() {
    }

    public static String safe(Object value) {
        return value == null ? null : String.valueOf(value).replaceAll("\\R", "_");
    }
}
