package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.BookmarkResponse;
import com.educonnect.postservice.service.PostBookmarkService;
import com.educonnect.postservice.service.Viewer;
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

    public PostBookmarkController(PostBookmarkService postBookmarkService) {
        this.postBookmarkService = postBookmarkService;
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
        BookmarkResponse response = postBookmarkService.toggleBookmark(postId, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.ok(response);
    }
}
