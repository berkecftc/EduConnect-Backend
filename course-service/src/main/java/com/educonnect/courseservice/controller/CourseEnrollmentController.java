package com.educonnect.courseservice.controller;

import com.educonnect.courseservice.dto.CourseApplicationResponse;
import com.educonnect.courseservice.dto.EnrollmentEventResponse;
import com.educonnect.courseservice.dto.StudentRemovalRequest;
import com.educonnect.courseservice.dto.WithdrawalRequest;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.service.CourseApplicationService;
import com.educonnect.courseservice.service.CourseEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/courses")
public class CourseEnrollmentController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final CourseEnrollmentService enrollmentService;
    private final CourseApplicationService applicationService;

    public CourseEnrollmentController(CourseEnrollmentService enrollmentService,
                                      CourseApplicationService applicationService) {
        this.enrollmentService = enrollmentService;
        this.applicationService = applicationService;
    }

    @PostMapping("/{courseId}/withdraw")
    public ResponseEntity<EnrollmentEventResponse> withdraw(@PathVariable UUID courseId,
                                                            @Valid @RequestBody(required = false) WithdrawalRequest request,
                                                            @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(enrollmentService.withdraw(courseId, UUID.fromString(userIdHeader),
                request != null ? request.reason() : null));
    }

    @DeleteMapping("/{courseId}/withdraw")
    public ResponseEntity<String> withdrawLegacy(@PathVariable UUID courseId,
                                                 @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        enrollmentService.withdraw(courseId, UUID.fromString(userIdHeader), null);
        return ResponseEntity.ok("Kurstan başarıyla çıkıldı");
    }

    @PostMapping("/{courseId}/students/{studentId}/remove")
    public ResponseEntity<EnrollmentEventResponse> remove(@PathVariable UUID courseId,
                                                          @PathVariable UUID studentId,
                                                          @Valid @RequestBody StudentRemovalRequest request,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(enrollmentService.remove(courseId, UUID.fromString(userIdHeader), studentId, request.reason()));
    }

    @GetMapping("/{courseId}/enrollment-history")
    public ResponseEntity<List<EnrollmentEventResponse>> courseHistory(@PathVariable UUID courseId,
                                                                       @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(enrollmentService.courseHistory(courseId, UUID.fromString(userIdHeader)));
    }

    @GetMapping("/my-enrollment-history")
    public ResponseEntity<List<EnrollmentEventResponse>> myHistory(@RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(enrollmentService.studentHistory(UUID.fromString(userIdHeader)));
    }

    @GetMapping("/{courseId}/applications")
    public ResponseEntity<List<CourseApplicationResponse>> applications(@PathVariable UUID courseId,
                                                                        @RequestParam(required = false) CourseApplicationStatus status,
                                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(applicationService.getApplications(courseId, UUID.fromString(userIdHeader), status));
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    public ResponseEntity<CourseApplicationResponse> withdrawApplication(@PathVariable UUID applicationId,
                                                                         @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(applicationService.withdrawApplication(applicationId, UUID.fromString(userIdHeader)));
    }
}
