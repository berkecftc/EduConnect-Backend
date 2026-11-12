package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.AppealDecisionRequest;
import com.educonnect.postservice.dto.AppealRequest;
import com.educonnect.postservice.dto.AppealResponse;
import com.educonnect.postservice.service.AppealService;
import com.educonnect.postservice.service.Viewer;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping("/{postId}/appeal")
    public ResponseEntity<AppealResponse> appealPost(
            @PathVariable UUID postId,
            @RequestBody @Valid AppealRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appealService.appealPost(postId, request.statement(), Viewer.reader(authenticatedUserId, roles)));
    }

    @PostMapping("/comments/{commentId}/appeal")
    public ResponseEntity<AppealResponse> appealComment(
            @PathVariable UUID commentId,
            @RequestBody @Valid AppealRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appealService.appealComment(commentId, request.statement(), Viewer.reader(authenticatedUserId, roles)));
    }

    @GetMapping("/appeals/me")
    public ResponseEntity<List<AppealResponse>> myAppeals(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.ok(appealService.myAppeals(Viewer.reader(authenticatedUserId, roles)));
    }

    @GetMapping("/moderation/appeals")
    public ResponseEntity<Page<AppealResponse>> openAppeals(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(appealService.openAppeals(Viewer.of(authenticatedUserId, roles), pageable));
    }

    @PostMapping("/moderation/appeals/{appealId}/decision")
    public ResponseEntity<AppealResponse> decide(
            @PathVariable UUID appealId,
            @RequestBody @Valid AppealDecisionRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.ok(appealService.decide(appealId, request, Viewer.of(authenticatedUserId, roles)));
    }
}
