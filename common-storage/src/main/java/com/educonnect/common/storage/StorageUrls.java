package com.educonnect.common.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class StorageUrls {

    public static final String DEFAULT_BASE_URL = "http://localhost:9000";

    private final String publicBaseUrl;
    private final List<String> knownBaseUrls;

    public StorageUrls(String publicBaseUrl, List<String> legacyBaseUrls) {
        this.publicBaseUrl = normalize(publicBaseUrl != null && !publicBaseUrl.isBlank() ? publicBaseUrl : DEFAULT_BASE_URL);
        Set<String> bases = new LinkedHashSet<>();
        bases.add(this.publicBaseUrl);
        if (legacyBaseUrls != null) {
            legacyBaseUrls.stream().filter(url -> url != null && !url.isBlank()).map(StorageUrls::normalize).forEach(bases::add);
        }
        bases.add(DEFAULT_BASE_URL);
        bases.add("http://127.0.0.1:9000");
        this.knownBaseUrls = new ArrayList<>(bases);
    }

    public static StorageUrls defaults() {
        return new StorageUrls(DEFAULT_BASE_URL, List.of());
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public String toStoredValue(String urlOrPath) {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            return urlOrPath;
        }
        for (String base : knownBaseUrls) {
            if (urlOrPath.startsWith(base + "/")) {
                return stripQuery(urlOrPath.substring(base.length() + 1));
            }
        }
        return urlOrPath;
    }

    public String toUrl(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }
        String path = toStoredValue(storedValue);
        if (isAbsolute(path)) {
            return path;
        }
        return publicBaseUrl + "/" + trimLeadingSlash(path);
    }

    public String objectName(String urlOrPath, String bucket) {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            return urlOrPath;
        }
        String path = trimLeadingSlash(toStoredValue(urlOrPath));
        String bucketPrefix = bucket + "/";
        return path.startsWith(bucketPrefix) ? path.substring(bucketPrefix.length()) : path;
    }

    public String url(String bucket, String objectName) {
        return publicBaseUrl + "/" + bucket + "/" + trimLeadingSlash(objectName);
    }

    private static boolean isAbsolute(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String stripQuery(String value) {
        int queryIndex = value.indexOf('?');
        return queryIndex >= 0 ? value.substring(0, queryIndex) : value;
    }

    private static String trimLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static String normalize(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
