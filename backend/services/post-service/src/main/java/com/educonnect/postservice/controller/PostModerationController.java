package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.ModerationDecision;
import com.educonnect.postservice.dto.ModerationDecisionRequest;
import com.educonnect.postservice.service.PostModerationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostModerationController {

    private static final Logger log = LoggerFactory.getLogger(PostModerationController.class);

    private final PostModerationService postModerationService;

    public PostModerationController(PostModerationService postModerationService) {
        this.postModerationService = postModerationService;
    }

    @PutMapping("/internal/{postId}/moderation")
    public ResponseEntity<Void> applyModeration(
            @PathVariable UUID postId,
            @RequestBody @Valid ModerationDecisionRequest request) {
        Optional<ModerationDecision> decision = ModerationDecision.from(request.decision());
        if (decision.isEmpty()) {
            log.warn("Invalid moderation decision received. postId={}, decision={}", postId, request.decision());
            return ResponseEntity.badRequest().build();
        }
        postModerationService.applyModeration(postId, decision.get(), request.fromWordList(), eventIdOf(request, postId));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/internal/comments/{commentId}/moderation")
    public ResponseEntity<Void> applyCommentModeration(
            @PathVariable UUID commentId,
            @RequestBody @Valid ModerationDecisionRequest request) {
        Optional<ModerationDecision> decision = ModerationDecision.from(request.decision());
        if (decision.isEmpty()) {
            log.warn("Invalid moderation decision received. commentId={}, decision={}", commentId, request.decision());
            return ResponseEntity.badRequest().build();
        }
        postModerationService.applyCommentModeration(commentId, decision.get(), request.fromWordList(),
                eventIdOf(request, commentId));
        return ResponseEntity.accepted().build();
    }

    private static UUID eventIdOf(ModerationDecisionRequest request, UUID targetId) {
        if (request.eventId() == null || request.eventId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(request.eventId());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid eventId received, ignoring. targetId={}, eventId={}", targetId, request.eventId());
            return null;
        }
    }
}
