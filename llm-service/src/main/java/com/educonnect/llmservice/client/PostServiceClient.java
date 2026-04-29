package com.educonnect.llmservice.client;

import com.educonnect.llmservice.dto.moderation.ModerationDecisionRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "post-service", path = "/api/posts")
public interface PostServiceClient {

    @PutMapping("/{postId}/moderation")
    void applyModerationDecision(
            @PathVariable("postId") String postId,
            @RequestBody ModerationDecisionRequest request
    );
}

