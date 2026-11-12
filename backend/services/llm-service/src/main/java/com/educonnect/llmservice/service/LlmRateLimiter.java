package com.educonnect.llmservice.service;

import com.educonnect.llmservice.config.LlmSafetyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmRateLimiter {

    static final long WINDOW_MILLIS = 60_000L;

    private final LlmSafetyProperties.RateLimit settings;
    private final Clock clock;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    @Autowired
    public LlmRateLimiter(LlmSafetyProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LlmRateLimiter(LlmSafetyProperties properties, Clock clock) {
        this.settings = properties.rateLimit();
        this.clock = clock;
    }

    public void acquire(String userId) {
        if (!settings.enabled()) {
            return;
        }
        long now = clock.millis();
        long[] window = windows.compute(userId, (key, current) ->
                current == null || now - current[0] >= WINDOW_MILLIS ? new long[]{now, 1} : new long[]{current[0], current[1] + 1});
        if (window[1] > settings.requestsPerMinute()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Asistana çok sık istek gönderildi. Lütfen biraz sonra tekrar deneyin.");
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue()[0] >= WINDOW_MILLIS);
        }
    }
}
