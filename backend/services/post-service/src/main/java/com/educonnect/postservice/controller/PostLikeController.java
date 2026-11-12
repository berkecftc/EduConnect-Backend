package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.LikeResponse;
import com.educonnect.postservice.service.PostLikeService;
import com.educonnect.postservice.service.Viewer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Beğeni endpoint'i.
 * Geriye uyumluluk için toggle yanında explicit like/unlike endpoint'leri de sunar.
 */
@RestController
@RequestMapping({"/api/posts/{postId}/like", "/api/posts/{postId}/likes"})
public class PostLikeController {

    private final PostLikeService postLikeService;

    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    /**
     * Post'u beğen (idempotent).
     */
    @PutMapping
    public ResponseEntity<LikeResponse> likePost(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        LikeResponse response = postLikeService.likePost(postId, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.ok(response);
    }

    /**
     * Post beğenisini kaldır (idempotent).
     */
    @DeleteMapping
    public ResponseEntity<LikeResponse> unlikePost(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        LikeResponse response = postLikeService.unlikePost(postId, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.ok(response);
    }

    /**
     * Post'u beğen veya beğeniyi geri al (toggle, geriye uyumluluk için).
     */
    @PostMapping
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        LikeResponse response = postLikeService.toggleLike(postId, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.ok(response);
    }
}
