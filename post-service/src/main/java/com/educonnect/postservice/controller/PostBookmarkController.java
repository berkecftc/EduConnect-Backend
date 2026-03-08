package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.BookmarkResponse;
import com.educonnect.postservice.service.PostBookmarkService;
import com.educonnect.postservice.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Kaydetme (bookmark) endpoint'i.
 * Toggle mantığı ile çalışır: Kaydedilmişse geri al, kaydedilmemişse kaydet.
 */
@RestController
@RequestMapping("/api/posts/{postId}/bookmark")
public class PostBookmarkController {

    private final PostBookmarkService postBookmarkService;
    private final PostService postService;

    public PostBookmarkController(PostBookmarkService postBookmarkService, PostService postService) {
        this.postBookmarkService = postBookmarkService;
        this.postService = postService;
    }

    /**
     * Post'u kaydet veya kaydı geri al (toggle).
     */
    @PostMapping
    public ResponseEntity<BookmarkResponse> toggleBookmark(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID userId = UUID.fromString(authenticatedUserId);
        BookmarkResponse response = postBookmarkService.toggleBookmark(postId, userId);
        return ResponseEntity.ok(response);
    }
}

