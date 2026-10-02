package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.AssignmentResponse;
import com.educonnect.assignmentservice.dto.GradeChangeResponse;
import com.educonnect.assignmentservice.dto.GradeSubmissionRequest;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.service.AssignmentAccessGuard;
import com.educonnect.assignmentservice.service.GradingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.educonnect.assignmentservice.service.AssignmentAccessGuard.parseUserId;

@RestController
@RequestMapping("/api/assignments")
public class GradingController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final GradingService gradingService;
    private final AssignmentAccessGuard accessGuard;

    public GradingController(GradingService gradingService, AssignmentAccessGuard accessGuard) {
        this.gradingService = gradingService;
        this.accessGuard = accessGuard;
    }

    @PutMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<String> grade(@PathVariable UUID submissionId,
                                        @RequestBody @Valid GradeSubmissionRequest request,
                                        @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                        @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        AssignmentSubmission submission = accessGuard.getSubmission(submissionId);
        Assignment assignment = accessGuard.getAssignment(submission.getAssignmentId());
        UUID userId = parseUserId(userIdHeader);
        accessGuard.requireGrader(assignment.getCourseId(), userId, roles);
        gradingService.grade(submission, assignment, request.getGrade(), request.getFeedback(), request.getReason(), userId);
        return ResponseEntity.ok("Not başarıyla verildi");
    }

    @PostMapping("/{assignmentId}/publish-grades")
    public ResponseEntity<AssignmentResponse> publish(@PathVariable UUID assignmentId,
                                                      @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                      @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        Assignment assignment = accessGuard.getAssignment(assignmentId);
        UUID userId = parseUserId(userIdHeader);
        accessGuard.requireGradePublisher(assignment.getCourseId(), userId, roles);
        return ResponseEntity.ok(gradingService.publish(assignment, userId));
    }

    @GetMapping("/submissions/{submissionId}/grade-history")
    public ResponseEntity<List<GradeChangeResponse>> history(@PathVariable UUID submissionId,
                                                             @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                             @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        AssignmentSubmission submission = accessGuard.getSubmission(submissionId);
        Assignment assignment = accessGuard.getAssignment(submission.getAssignmentId());
        accessGuard.requireStaff(assignment.getCourseId(), parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(gradingService.history(submissionId));
    }
}
