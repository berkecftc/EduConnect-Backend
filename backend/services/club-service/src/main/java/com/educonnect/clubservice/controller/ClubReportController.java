package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.ActivityReportRequest;
import com.educonnect.clubservice.dto.request.AuditReportRequest;
import com.educonnect.clubservice.dto.request.FinanceNoteRequest;
import com.educonnect.clubservice.dto.request.NoteRequest;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.dto.response.ReportResponse;
import com.educonnect.clubservice.dto.response.ReportSnapshotResponse;
import com.educonnect.clubservice.service.ClubGovernanceService;
import com.educonnect.clubservice.service.ClubReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs/{clubId}/reports")
@PreAuthorize("isAuthenticated()")
public class ClubReportController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubReportService reportService;
    private final ClubGovernanceService governanceService;

    public ClubReportController(ClubReportService reportService, ClubGovernanceService governanceService) {
        this.reportService = reportService;
        this.governanceService = governanceService;
    }

    @GetMapping
    public ResponseEntity<List<ReportResponse>> reports(@PathVariable UUID clubId,
                                                        @RequestParam(required = false) Integer academicYear,
                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(reportService.reportsOf(clubId, UUID.fromString(userIdHeader), academicYear));
    }

    @GetMapping("/preview/{academicYear}")
    public ResponseEntity<ReportSnapshotResponse> preview(@PathVariable UUID clubId,
                                                          @PathVariable int academicYear,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(reportService.preview(clubId, UUID.fromString(userIdHeader), academicYear));
    }

    @GetMapping("/{type}/{academicYear}")
    public ResponseEntity<ReportResponse> report(@PathVariable UUID clubId,
                                                 @PathVariable String type,
                                                 @PathVariable int academicYear,
                                                 @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(reportService.reportOf(clubId, UUID.fromString(userIdHeader),
                ClubReportService.typeOf(type), academicYear));
    }

    @PutMapping("/activity/{academicYear}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ReportResponse> writeActivity(@PathVariable UUID clubId,
                                                        @PathVariable int academicYear,
                                                        @Valid @RequestBody ActivityReportRequest request,
                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(reportService.writeActivity(clubId, UUID.fromString(userIdHeader), academicYear,
                request.summary()));
    }

    @PutMapping("/activity/{academicYear}/finance-note")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ReportResponse> writeFinanceNote(@PathVariable UUID clubId,
                                                           @PathVariable int academicYear,
                                                           @Valid @RequestBody FinanceNoteRequest request,
                                                           @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(reportService.writeFinanceNote(clubId, UUID.fromString(userIdHeader), academicYear,
                request.financeNote()));
    }

    @PutMapping("/audit/{academicYear}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ReportResponse> writeAudit(@PathVariable UUID clubId,
                                                     @PathVariable int academicYear,
                                                     @Valid @RequestBody AuditReportRequest request,
                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(reportService.writeAudit(clubId, UUID.fromString(userIdHeader), academicYear,
                request.findings(), request.recommendations()));
    }

    @PostMapping("/{type}/{academicYear}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApprovalRequestResponse> submit(@PathVariable UUID clubId,
                                                          @PathVariable String type,
                                                          @PathVariable int academicYear,
                                                          @Valid @RequestBody(required = false) NoteRequest request,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(reportService.submit(clubId,
                UUID.fromString(userIdHeader), ClubReportService.typeOf(type), academicYear,
                request != null ? request.note() : null)));
    }
}
