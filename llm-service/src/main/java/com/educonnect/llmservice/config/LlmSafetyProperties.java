package com.educonnect.llmservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "educonnect.llm")
public record LlmSafetyProperties(RateLimit rateLimit, Memory memory, Moderation moderation) {

    public LlmSafetyProperties {
        rateLimit = rateLimit != null ? rateLimit : new RateLimit(false, 0);
        memory = memory != null ? memory : new Memory(0, 0, null);
        moderation = moderation != null ? moderation : new Moderation(null, 0);
    }

    public record RateLimit(boolean enabled, int requestsPerMinute) {

        public RateLimit {
            requestsPerMinute = requestsPerMinute > 0 ? requestsPerMinute : 10;
        }
    }

    public record Memory(int maxConversations, int maxMessages, Duration ttl) {

        public Memory {
            maxConversations = maxConversations > 0 ? maxConversations : 500;
            maxMessages = maxMessages > 0 ? maxMessages : 20;
            ttl = ttl != null ? ttl : Duration.ofMinutes(30);
        }
    }

    public record Moderation(List<String> blockedTerms, int maxAttempts) {

        public Moderation {
            blockedTerms = blockedTerms != null ? List.copyOf(blockedTerms) : List.of();
            maxAttempts = maxAttempts > 0 ? maxAttempts : 2;
        }
    }
}
