package com.educonnect.common.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresignedUrlsTest {

    @Test
    void get_withPublicEndpoint_shouldSignForPublicHostWithoutContactingServer() {
        StorageUrls storageUrls = new StorageUrls("https://files.educonnect.invalid", List.of("http://minio:9000"));
        PresignedUrls presignedUrls = new PresignedUrls(storageUrls, "access", "secret-key-123");

        String url = presignedUrls.get("educonnect-documents", "id-cards/a.jpg", Duration.ofMinutes(15));

        assertThat(url).startsWith("https://files.educonnect.invalid/educonnect-documents/id-cards/a.jpg?");
        assertThat(url).contains("X-Amz-Signature=", "X-Amz-Expires=900", "us-east-1");
    }

    @Test
    void get_withLocalEndpoint_shouldKeepLocalHost() {
        PresignedUrls presignedUrls = new PresignedUrls(new StorageUrls("http://localhost:9000", List.of()), "access", "secret-key-123");

        String url = presignedUrls.get("club-logos", "c1.png", Duration.ofDays(7));

        assertThat(url).startsWith("http://localhost:9000/club-logos/c1.png?").contains("X-Amz-Expires=604800");
    }

    @Test
    void constructor_withPathInPublicEndpoint_shouldFailFast() {
        StorageUrls storageUrls = new StorageUrls("https://educonnect.invalid/storage", List.of());

        assertThatThrownBy(() -> new PresignedUrls(storageUrls, "access", "secret-key-123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
