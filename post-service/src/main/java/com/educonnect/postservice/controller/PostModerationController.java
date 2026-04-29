package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.ModerationDecision;
import com.educonnect.postservice.dto.ModerationDecisionRequest;
import com.educonnect.postservice.service.PostModerationService;
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

    @PutMapping("/{postId}/moderation")
    public ResponseEntity<Void> applyModeration(
            @PathVariable UUID postId,
            @RequestBody ModerationDecisionRequest request) {

        Optional<ModerationDecision> decision = ModerationDecision.from(request.decision());
        if (decision.isEmpty()) {
            log.warn("Invalid moderation decision received. postId={}, decision={}", postId, request.decision());
            return ResponseEntity.badRequest().build();
        }

        UUID eventId = null;
        if (request.eventId() != null && !request.eventId().isBlank()) {
            try {
                eventId = UUID.fromString(request.eventId());
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid eventId received, ignoring. postId={}, eventId={}", postId, request.eventId());
            }
        }

        postModerationService.applyModeration(postId, decision.get(), eventId);
        return ResponseEntity.accepted().build();
    }
}

