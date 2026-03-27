package com.educonnect.userservice.client.fallback;

import com.educonnect.userservice.client.PostClient;
import com.educonnect.userservice.client.dto.RecentPostClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PostClientFallbackFactory implements FallbackFactory<PostClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostClientFallbackFactory.class);

    @Override
    public PostClient create(Throwable cause) {
        String reason = cause != null ? cause.getMessage() : "unknown";
        return new PostClient() {
            @Override
            public List<RecentPostClientResponse> getRecentPosts(UUID userId) {
                LOGGER.warn("post-service fallback devrede. userId={}, reason={}", userId, reason);
                return List.of();
            }
        };
    }
}



