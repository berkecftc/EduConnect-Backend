package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.GradebookResponse;
import com.educonnect.assignmentservice.dto.MyGradesResponse;
import com.educonnect.assignmentservice.service.AssignmentAccessGuard;
import com.educonnect.assignmentservice.service.GradebookService;
import com.educonnect.common.storage.SafeFileNames;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.educonnect.assignmentservice.service.AssignmentAccessGuard.parseUserId;

@RestController
@RequestMapping("/api/assignments/course/{courseId}")
public class GradebookController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final GradebookService gradebookService;
    private final AssignmentAccessGuard accessGuard;

    public GradebookController(GradebookService gradebookService, AssignmentAccessGuard accessGuard) {
        this.gradebookService = gradebookService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/gradebook")
    public ResponseEntity<GradebookResponse> gradebook(@PathVariable UUID courseId,
                                                       @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                       @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        accessGuard.requireStaff(courseId, parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(gradebookService.gradebook(courseId));
    }

    @GetMapping("/gradebook.csv")
    public ResponseEntity<String> gradebookCsv(@PathVariable UUID courseId,
                                               @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                               @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        accessGuard.requireStaff(courseId, parseUserId(userIdHeader), roles);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, SafeFileNames.attachmentHeader("not-defteri-" + courseId + ".csv"))
                .header("X-Content-Type-Options", "nosniff")
                .body(gradebookService.csv(courseId));
    }

    @GetMapping("/my-grades")
    public ResponseEntity<MyGradesResponse> myGrades(@PathVariable UUID courseId,
                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        UUID studentId = parseUserId(userIdHeader);
        accessGuard.requireEnrolled(courseId, studentId);
        return ResponseEntity.ok(gradebookService.myGrades(courseId, studentId));
    }
}
