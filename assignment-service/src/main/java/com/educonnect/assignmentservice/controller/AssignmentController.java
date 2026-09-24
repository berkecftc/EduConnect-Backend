package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.*;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.service.AssignmentAccessGuard;
import com.educonnect.assignmentservice.service.AssignmentService;
import com.educonnect.assignmentservice.service.MinioService;
import com.educonnect.common.storage.SafeFileNames;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

import static com.educonnect.assignmentservice.service.AssignmentAccessGuard.parseUserId;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final AssignmentService assignmentService;
    private final AssignmentAccessGuard accessGuard;
    private final MinioService minioService;

    public AssignmentController(AssignmentService service,
                                AssignmentAccessGuard accessGuard,
                                MinioService minioService) {
        this.assignmentService = service;
        this.accessGuard = accessGuard;
        this.minioService = minioService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<AssignmentResponse> create(
            @RequestPart("assignment") AssignmentRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader(USER_ID_HEADER) String userIdHeader,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles
    ) {
        accessGuard.requireInstructor(request.getCourseId(), parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(assignmentService.createAssignment(request, file));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<AssignmentResponse>> getByCourse(
            @PathVariable UUID courseId,
            @RequestHeader(USER_ID_HEADER) String userIdHeader,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles
    ) {
        accessGuard.requireCourseMember(courseId, parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(assignmentService.getAssignmentsByCourse(courseId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(USER_ID_HEADER) String userIdHeader,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles
    ) {
        Assignment assignment = accessGuard.getAssignment(id);
        accessGuard.requireInstructor(assignment.getCourseId(), parseUserId(userIdHeader), roles);
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    // ÖĞRENCİ ÖDEV TESLİMİ
    @PostMapping(value = "/{assignmentId}/submit", consumes = {"multipart/form-data"})
    public ResponseEntity<?> submitAssignment(
            @PathVariable UUID assignmentId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader(USER_ID_HEADER) String studentIdHeader
    ) {
        UUID studentId = parseUserId(studentIdHeader);
        Assignment assignment = accessGuard.getAssignment(assignmentId);
        accessGuard.requireEnrolledStudent(assignment.getCourseId(), studentId);
        try {
            AssignmentSubmission submission = assignmentService.submitAssignment(assignmentId, studentId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(submission);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // AKADEMİSYEN NOT VERME
    @PutMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<String> gradeSubmission(
            @PathVariable UUID submissionId,
            @RequestBody GradeSubmissionRequest request,
            @RequestHeader(USER_ID_HEADER) String userIdHeader,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles
    ) {
        AssignmentSubmission submission = accessGuard.getSubmission(submissionId);
        Assignment assignment = accessGuard.getAssignment(submission.getAssignmentId());
        accessGuard.requireInstructor(assignment.getCourseId(), parseUserId(userIdHeader), roles);
        try {
            assignmentService.gradeSubmission(submissionId, request.getGrade(), request.getFeedback());
            return ResponseEntity.ok("Not başarıyla verildi");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // BİR DERSE AİT TÜM TESLİMLERİ GETİR (Akademisyen)
    @GetMapping("/course/{courseId}/submissions")
    public ResponseEntity<List<SubmissionSummaryDTO>> getCourseSubmissions(
            @PathVariable UUID courseId,
            @RequestHeader(USER_ID_HEADER) String userIdHeader,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles
    ) {
        accessGuard.requireInstructor(courseId, parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(assignmentService.getSubmissionsByCourse(courseId));
    }

    // BİR ÖDEVE AİT TÜM TESLİMLERİ GETİR (Akademisyen)
    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<List<SubmissionSummaryDTO>> getAssignmentSubmissions(
            @PathVariable UUID assignmentId,
            @RequestHeader(USER_ID_HEADER) String userIdHeader,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles
    ) {
        Assignment assignment = accessGuard.getAssignment(assignmentId);
        accessGuard.requireInstructor(assignment.getCourseId(), parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(assignmentService.getSubmissionsByAssignment(assignmentId));
    }

    // ÖĞRENCİNİN TÜM ÖDEVLERİNİ GETİR
    @GetMapping("/my-assignments")
    public ResponseEntity<List<MyAssignmentDTO>> getMyAssignments(
            @RequestHeader(USER_ID_HEADER) String studentIdHeader
    ) {
        return ResponseEntity.ok(assignmentService.getStudentAssignments(parseUserId(studentIdHeader)));
    }

    // DOSYA İNDİRME (Ödev dökümanı veya teslim dosyası)
    @GetMapping("/files/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam("url") String fileUrl,
            @RequestHeader(USER_ID_HEADER) String userIdHeader,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles
    ) {
        String normalizedUrl = minioService.normalizeToFullUrl(fileUrl);
        accessGuard.requireFileAccess(normalizedUrl, parseUserId(userIdHeader), roles);
        try {
            Resource resource = assignmentService.downloadFile(normalizedUrl);
            String fileName = assignmentService.getOriginalFileName(normalizedUrl);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, SafeFileNames.attachmentHeader(fileName))
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
