package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.LikeResponse;
import com.educonnect.postservice.service.PostLikeService;
import com.educonnect.postservice.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Beğeni endpoint'i.
 * Toggle mantığı ile çalışır: Beğenmişse geri al, beğenmemişse beğen.
 */
@RestController
@RequestMapping("/api/posts/{postId}/like")
public class PostLikeController {

    private final PostLikeService postLikeService;
    private final PostService postService;

    public PostLikeController(PostLikeService postLikeService, PostService postService) {
        this.postLikeService = postLikeService;
        this.postService = postService;
    }

    /**
     * Post'u beğen veya beğeniyi geri al (toggle).
     */
    @PostMapping
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID userId = UUID.fromString(authenticatedUserId);
        LikeResponse response = postLikeService.toggleLike(postId, userId);
        return ResponseEntity.ok(response);
    }
}

