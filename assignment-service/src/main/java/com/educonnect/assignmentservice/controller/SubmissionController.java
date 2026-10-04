package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.SubmissionResponse;
import com.educonnect.assignmentservice.dto.SubmissionVersionResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.service.AssignmentAccessGuard;
import com.educonnect.assignmentservice.service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.educonnect.assignmentservice.service.AssignmentAccessGuard.parseUserId;

@RestController
@RequestMapping("/api/assignments")
public class SubmissionController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final SubmissionService submissionService;
    private final AssignmentAccessGuard accessGuard;

    public SubmissionController(SubmissionService submissionService, AssignmentAccessGuard accessGuard) {
        this.submissionService = submissionService;
        this.accessGuard = accessGuard;
    }

    @PostMapping(value = "/{assignmentId}/submit", consumes = {"multipart/form-data"})
    public ResponseEntity<SubmissionResponse> submit(@PathVariable UUID assignmentId,
                                                     @RequestPart(value = "file", required = false) MultipartFile file,
                                                     @RequestParam(value = "text", required = false) String text,
                                                     @RequestParam(value = "aiUsed", required = false) Boolean aiUsed,
                                                     @RequestParam(value = "aiNote", required = false) String aiNote,
                                                     @RequestHeader(USER_ID_HEADER) String studentIdHeader) {
        UUID studentId = parseUserId(studentIdHeader);
        Assignment assignment = accessGuard.getAssignment(assignmentId);
        accessGuard.requireEnrolledStudent(assignment.getCourseId(), studentId);
        AssignmentSubmission submission = submissionService.submit(assignmentId, studentId, file, text, aiUsed, aiNote);
        return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponse.from(submission));
    }

    @GetMapping("/submissions/{submissionId}/versions")
    public ResponseEntity<List<SubmissionVersionResponse>> versions(@PathVariable UUID submissionId,
                                                                    @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                                    @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        accessGuard.requireSubmissionViewer(accessGuard.getSubmission(submissionId), parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(submissionService.versions(submissionId));
    }
}
