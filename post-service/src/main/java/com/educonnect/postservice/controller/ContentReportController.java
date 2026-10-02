package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.ReasonRequest;
import com.educonnect.postservice.dto.ReportRequest;
import com.educonnect.postservice.dto.ReportResponse;
import com.educonnect.postservice.service.ContentControlService;
import com.educonnect.postservice.service.ContentReportService;
import com.educonnect.postservice.service.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class ContentReportController {

    private final ContentReportService reportService;
    private final ContentControlService contentControl;

    public ContentReportController(ContentReportService reportService, ContentControlService contentControl) {
        this.reportService = reportService;
        this.contentControl = contentControl;
    }

    @PostMapping("/{postId}/report")
    public ResponseEntity<ReportResponse> reportPost(
            @PathVariable UUID postId,
            @RequestBody @Valid ReportRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.reportPost(postId, request, Viewer.reader(authenticatedUserId, roles)));
    }

    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<ReportResponse> reportComment(
            @PathVariable UUID commentId,
            @RequestBody @Valid ReportRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.reportComment(commentId, request, Viewer.reader(authenticatedUserId, roles)));
    }

    @PostMapping("/{postId}/hide")
    public ResponseEntity<Void> hidePost(
            @PathVariable UUID postId,
            @RequestBody @Valid ReasonRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        contentControl.hidePost(postId, request.reason(), Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/hide")
    public ResponseEntity<Void> hideComment(
            @PathVariable UUID commentId,
            @RequestBody @Valid ReasonRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        contentControl.hideComment(commentId, request.reason(), Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }
}
