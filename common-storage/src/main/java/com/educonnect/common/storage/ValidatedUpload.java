package com.educonnect.common.storage;

public record ValidatedUpload(String contentType, String extension, String safeOriginalName) {
}
