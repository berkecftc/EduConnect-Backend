package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.AcceptAnswerRequest;
import com.educonnect.postservice.service.AnswerService;
import com.educonnect.postservice.service.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts/{postId}/accepted-answer")
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PutMapping
    public ResponseEntity<Void> accept(
            @PathVariable UUID postId,
            @RequestBody @Valid AcceptAnswerRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        answerService.accept(postId, request.commentId(), Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unaccept(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        answerService.unaccept(postId, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }
}
