package com.educonnect.userservice.client;

import com.educonnect.userservice.client.dto.RecentPostClientResponse;
import com.educonnect.userservice.client.fallback.PostClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "post-service",
        path = "/api/posts",
        fallbackFactory = PostClientFallbackFactory.class
)
public interface PostClient {

    @GetMapping("/internal/users/{userId}/recent")
    List<RecentPostClientResponse> getRecentPosts(@PathVariable("userId") UUID userId);
}

