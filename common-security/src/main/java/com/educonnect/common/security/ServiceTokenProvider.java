package com.educonnect.common.security;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ServiceTokenProvider {

    private static final ParameterizedTypeReference<Map<String, Object>> TOKEN_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final Duration refreshSkew;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile CachedToken cached;

    public ServiceTokenProvider(RestClient restClient, String clientId, String clientSecret,
                                String tokenUri, Duration refreshSkew, Clock clock) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.refreshSkew = refreshSkew;
        this.clock = clock;
    }

    public String getToken() {
        CachedToken current = cached;
        if (current != null && current.isUsableAt(clock.instant())) {
            return current.value();
        }
        lock.lock();
        try {
            current = cached;
            if (current == null || !current.isUsableAt(clock.instant())) {
                current = requestToken();
                cached = current;
            }
            return current.value();
        } finally {
            lock.unlock();
        }
    }

    private CachedToken requestToken() {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new IllegalStateException(
                    "educonnect.security.service-client.id and .secret must be set to call internal endpoints");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        Map<String, Object> response = restClient.post()
                .uri(tokenUri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(TOKEN_RESPONSE);

        Object accessToken = response == null ? null : response.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("Token endpoint returned no access_token");
        }
        long expiresIn = response.get("expires_in") instanceof Number number ? number.longValue() : 0L;
        Instant refreshAt = clock.instant().plusSeconds(expiresIn).minus(refreshSkew);
        return new CachedToken(token, refreshAt);
    }

    private record CachedToken(String value, Instant refreshAt) {

        boolean isUsableAt(Instant now) {
            return now.isBefore(refreshAt);
        }
    }
}
