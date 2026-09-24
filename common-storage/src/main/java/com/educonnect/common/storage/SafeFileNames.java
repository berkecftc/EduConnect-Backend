package com.educonnect.common.storage;

import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

public final class SafeFileNames {

    private static final int MAX_LENGTH = 100;
    private static final String FALLBACK = "dosya";

    private SafeFileNames() {
    }

    public static String sanitize(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return FALLBACK;
        }
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        StringBuilder safe = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
                safe.append(c);
            } else if (Character.isWhitespace(c)) {
                safe.append('_');
            }
        }
        String result = safe.toString().replaceAll("_{2,}", "_").replaceAll("\\.{2,}", ".");
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        if (result.length() > MAX_LENGTH) {
            String extension = extensionOf(result);
            int keep = Math.max(1, MAX_LENGTH - extension.length());
            result = result.substring(0, keep) + extension;
        }
        return result.isBlank() ? FALLBACK : result;
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    public static String attachmentHeader(String fileName) {
        return ContentDisposition.attachment()
                .filename(sanitize(fileName), StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
