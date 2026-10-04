package com.educonnect.notificationservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "educonnect.notification")
public record NotificationProperties(@NotBlank String unsubscribeSecret,
                                     String frontendBaseUrl,
                                     String apiBaseUrl,
                                     Duration retention) {

    public NotificationProperties {
        frontendBaseUrl = trim(frontendBaseUrl != null ? frontendBaseUrl : "http://localhost:5173");
        apiBaseUrl = trim(apiBaseUrl != null ? apiBaseUrl : "http://localhost:8080");
        retention = retention != null ? retention : Duration.ofDays(180);
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
