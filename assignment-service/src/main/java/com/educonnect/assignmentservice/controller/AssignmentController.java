package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.*;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.service.AssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService service) {
        this.assignmentService = service;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<AssignmentResponse> create(
            @RequestPart("assignment") AssignmentRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(assignmentService.createAssignment(request, file));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<AssignmentResponse>> getByCourse(@PathVariable UUID courseId) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByCourse(courseId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    // ÖĞRENCİ ÖDEV TESLİMİ
    @PostMapping(value = "/{assignmentId}/submit", consumes = {"multipart/form-data"})
    public ResponseEntity<?> submitAssignment(
            @PathVariable UUID assignmentId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        try {
            UUID studentId = UUID.fromString(studentIdHeader);
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
            @RequestBody GradeSubmissionRequest request
    ) {
        try {
            assignmentService.gradeSubmission(submissionId, request.getGrade(), request.getFeedback());
            return ResponseEntity.ok("Not başarıyla verildi");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // BİR DERSE AİT TÜM TESLİMLERİ GETİR (Akademisyen)
    @GetMapping("/course/{courseId}/submissions")
    public ResponseEntity<List<SubmissionSummaryDTO>> getCourseSubmissions(@PathVariable UUID courseId) {
        return ResponseEntity.ok(assignmentService.getSubmissionsByCourse(courseId));
    }

    // BİR ÖDEVE AİT TÜM TESLİMLERİ GETİR (Akademisyen)
    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<List<SubmissionSummaryDTO>> getAssignmentSubmissions(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(assignmentService.getSubmissionsByAssignment(assignmentId));
    }

    // ÖĞRENCİNİN TÜM ÖDEVLERİNİ GETİR
    @GetMapping("/my-assignments")
    public ResponseEntity<List<MyAssignmentDTO>> getMyAssignments(
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        UUID studentId = UUID.fromString(studentIdHeader);
        return ResponseEntity.ok(assignmentService.getStudentAssignments(studentId));
    }
}