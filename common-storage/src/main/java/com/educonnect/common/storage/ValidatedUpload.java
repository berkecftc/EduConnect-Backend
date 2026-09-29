package com.educonnect.common.storage;

public record ValidatedUpload(String contentType, String extension, String safeOriginalName) {

    public String extensionOr(String fallback) {
        return extension.isEmpty() ? fallback : extension;
    }
}
