package com.educonnect.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "educonnect.security.service-client")
public record ServiceClientProperties(String id,
                                      String secret,
                                      String tokenUri,
                                      Duration refreshSkew,
                                      Duration timeout) {

    public static final String DEFAULT_TOKEN_URI = "http://auth-services/api/auth/internal/token";

    public ServiceClientProperties {
        if (tokenUri == null || tokenUri.isBlank()) {
            tokenUri = DEFAULT_TOKEN_URI;
        }
        if (refreshSkew == null) {
            refreshSkew = Duration.ofSeconds(60);
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(5);
        }
    }
}
