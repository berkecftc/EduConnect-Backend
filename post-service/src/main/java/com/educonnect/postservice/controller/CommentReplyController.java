package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.CommentResponse;
import com.educonnect.postservice.dto.CreateCommentRequest;
import com.educonnect.postservice.service.CommentService;
import com.educonnect.postservice.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Yorum yanıt (reply) endpoint'leri.
 * Yanıt işlemleri doğrudan commentId üzerinden yapılır: /api/posts/comments/{commentId}/replies
 *
 * Özellikler:
 * - Bir yoruma yanıt verme (tek seviye — yanıta yanıt verilemez)
 * - Bir yorumun yanıtlarını listeleme
 */
@RestController
@RequestMapping("/api/posts/comments/{commentId}/replies")
public class CommentReplyController {

    private final CommentService commentService;
    private final PostService postService;

    public CommentReplyController(CommentService commentService, PostService postService) {
        this.commentService = commentService;
        this.postService = postService;
    }

    /**
     * Bir yoruma yanıt verir.
     * Sadece üst seviye yorumlara yanıt verilebilir — yanıta yanıt verilmez.
     * İçerik blacklist kontrolünden geçer.
     */
    @PostMapping
    public ResponseEntity<CommentResponse> createReply(
            @PathVariable UUID commentId,
            @RequestBody @Valid CreateCommentRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        UUID authorId = UUID.fromString(authenticatedUserId);
        CommentResponse response = commentService.createReply(commentId, request.content(), authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Bir yorumun yayınlanmış yanıtlarını listeler.
     */
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getReplies(
            @PathVariable UUID commentId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.validatePostAccess(roles);
        List<CommentResponse> replies = commentService.getRepliesByCommentId(commentId);
        return ResponseEntity.ok(replies);
    }
}

