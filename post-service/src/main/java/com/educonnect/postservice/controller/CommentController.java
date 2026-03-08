package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.CommentResponse;
import com.educonnect.postservice.dto.CreateCommentRequest;
import com.educonnect.postservice.service.CommentService;
import com.educonnect.postservice.service.PostService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Yorum endpoint'leri.
 * Tüm yorum işlemleri post bağlamında yapılır: /api/posts/{postId}/comments
 *
 * Özellikler:
 * - Yorum oluşturma (parentCommentId ile yanıt verme dahil — tek seviye)
 * - Yorumları listeleme (üst yorumlar + iç içe replies)
 * - Yorum silme (sadece yazar)
 */
@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final PostService postService;

    public CommentController(CommentService commentService, PostService postService) {
        this.commentService = commentService;
        this.postService = postService;
    }

    /**
     * Yeni yorum oluşturur veya bir üst yoruma yanıt verir.
     * parentCommentId null ise üst seviye yorum, dolu ise yanıt.
     * İçerik blacklist kontrolünden geçer.
     */
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable UUID postId,
            @RequestBody @Valid CreateCommentRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID authorId = UUID.fromString(authenticatedUserId);
        CommentResponse response = commentService.createComment(postId, request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Post'a ait yayınlanmış yorumları sayfalayarak listeler.
     * Her üst yorumun yanıtları (replies) da eklenir.
     */
    @GetMapping
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable
    ) {
        postService.validatePostAccess(roles);
        return ResponseEntity.ok(commentService.getCommentsByPostId(postId, pageable));
    }

    /**
     * Yorumu siler.
     * Sadece yorum yazarı silebilir.
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID authorId = UUID.fromString(authenticatedUserId);
        commentService.deleteComment(postId, commentId, authorId);
        return ResponseEntity.noContent().build();
    }
}

