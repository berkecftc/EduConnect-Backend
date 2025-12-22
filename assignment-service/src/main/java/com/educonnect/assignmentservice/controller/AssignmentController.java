package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.AssignmentRequest;
import com.educonnect.assignmentservice.dto.AssignmentResponse;
import com.educonnect.assignmentservice.service.AssignmentService;
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
}