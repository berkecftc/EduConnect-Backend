package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.ModerationQueueItem;
import com.educonnect.postservice.dto.ModerationReportItem;
import com.educonnect.postservice.dto.ReasonRequest;
import com.educonnect.postservice.dto.ModerationRecordResponse;
import com.educonnect.postservice.dto.ModeratorDecisionRequest;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.service.ContentControlService;
import com.educonnect.postservice.service.ContentReportService;
import com.educonnect.postservice.service.ModerationQueueService;
import com.educonnect.postservice.service.PostModerationService;
import com.educonnect.postservice.service.Viewer;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts/moderation")
public class ModerationController {

    private final ModerationQueueService queueService;
    private final PostModerationService moderationService;
    private final ContentReportService reportService;
    private final ContentControlService contentControl;

    public ModerationController(ModerationQueueService queueService, PostModerationService moderationService,
                                ContentReportService reportService, ContentControlService contentControl) {
        this.queueService = queueService;
        this.moderationService = moderationService;
        this.reportService = reportService;
        this.contentControl = contentControl;
    }

    @GetMapping("/queue")
    public ResponseEntity<Page<ModerationQueueItem>> queue(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @RequestParam(defaultValue = "POST") ModerationTarget type,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(queueService.queue(type, Viewer.of(authenticatedUserId, roles), pageable));
    }

    @PostMapping("/posts/{postId}/decision")
    public ResponseEntity<Void> decidePost(
            @PathVariable UUID postId,
            @RequestBody @Valid ModeratorDecisionRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        moderationService.decidePost(postId, request, Viewer.of(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/decision")
    public ResponseEntity<Void> decideComment(
            @PathVariable UUID commentId,
            @RequestBody @Valid ModeratorDecisionRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        moderationService.decideComment(commentId, request, Viewer.of(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports")
    public ResponseEntity<Page<ModerationReportItem>> reports(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.openReports(Viewer.of(authenticatedUserId, roles), pageable));
    }

    @PostMapping("/reports/{reportId}/dismiss")
    public ResponseEntity<Void> dismissReport(
            @PathVariable UUID reportId,
            @RequestBody @Valid ReasonRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        reportService.dismiss(reportId, request.reason(), Viewer.of(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/remove")
    public ResponseEntity<Void> removePost(
            @PathVariable UUID postId,
            @RequestBody @Valid ReasonRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        contentControl.removePost(postId, request.reason(), Viewer.of(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/restore")
    public ResponseEntity<Void> restorePost(
            @PathVariable UUID postId,
            @RequestBody @Valid ReasonRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        contentControl.restorePost(postId, request.reason(), Viewer.of(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/remove")
    public ResponseEntity<Void> removeComment(
            @PathVariable UUID commentId,
            @RequestBody @Valid ReasonRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        contentControl.removeComment(commentId, request.reason(), Viewer.of(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/restore")
    public ResponseEntity<Void> restoreComment(
            @PathVariable UUID commentId,
            @RequestBody @Valid ReasonRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        contentControl.restoreComment(commentId, request.reason(), Viewer.of(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<List<ModerationRecordResponse>> history(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @RequestParam ModerationTarget targetType,
            @RequestParam UUID targetId
    ) {
        return ResponseEntity.ok(queueService.history(targetType, targetId, Viewer.of(authenticatedUserId, roles)));
    }
}
