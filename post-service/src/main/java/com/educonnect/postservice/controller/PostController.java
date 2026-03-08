package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.CreatePostRequest;
import com.educonnect.postservice.dto.PostResponse;
import com.educonnect.postservice.dto.UpdatePostRequest;
import com.educonnect.postservice.service.PostService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * Yeni post oluşturur.
     * Sadece ROLE_STUDENT ve ROLE_CLUB_OFFICIAL rolleri erişebilir.
     */
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @RequestBody @Valid CreatePostRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID authorId = UUID.fromString(authenticatedUserId);
        PostResponse response = postService.createPost(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mevcut post'u günceller.
     * Sadece ROLE_STUDENT ve ROLE_CLUB_OFFICIAL rolleri erişebilir.
     * Sadece yazar güncelleyebilir — yetki kontrolü Service katmanında yapılır.
     */
    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID postId,
            @RequestBody @Valid UpdatePostRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID authorId = UUID.fromString(authenticatedUserId);
        PostResponse response = postService.updatePost(postId, request, authorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Post'u siler.
     * Sadece ROLE_STUDENT ve ROLE_CLUB_OFFICIAL rolleri erişebilir.
     * Sadece yazar silebilir — yetki kontrolü Service katmanında yapılır.
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID authorId = UUID.fromString(authenticatedUserId);
        postService.deletePost(postId, authorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Yayınlanmış postları sayfalayarak listeler.
     * Sadece ROLE_STUDENT ve ROLE_CLUB_OFFICIAL rolleri erişebilir.
     */
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getPublishedPosts(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable
    ) {
        postService.validatePostAccess(roles);
        UUID currentUserId = UUID.fromString(authenticatedUserId);
        return ResponseEntity.ok(postService.getPublishedPosts(pageable, currentUserId));
    }

    /**
     * Kullanıcının kaydettiği (bookmark) postları sayfalayarak listeler.
     * Sadece ROLE_STUDENT ve ROLE_CLUB_OFFICIAL rolleri erişebilir.
     */
    @GetMapping("/saved")
    public ResponseEntity<Page<PostResponse>> getSavedPosts(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable
    ) {
        postService.validatePostAccess(roles);
        UUID currentUserId = UUID.fromString(authenticatedUserId);
        return ResponseEntity.ok(postService.getSavedPosts(currentUserId, pageable));
    }

    /**
     * Tek bir post'u ID'siyle getirir.
     * Sadece ROLE_STUDENT ve ROLE_CLUB_OFFICIAL rolleri erişebilir.
     */
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPostById(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID currentUserId = UUID.fromString(authenticatedUserId);
        return ResponseEntity.ok(postService.getPostById(postId, currentUserId));
    }
}

