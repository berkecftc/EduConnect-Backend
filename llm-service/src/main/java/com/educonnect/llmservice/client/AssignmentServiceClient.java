package com.educonnect.llmservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.List;

@FeignClient(name = "assignment-service", path = "/api/assignments")
public interface AssignmentServiceClient {

    // Temiz kod prensibi gereği record kullanıyoruz.
    record AssignmentResponse(
            String id,
            String title,
            String description,
            String dueDate,
            String courseId,
            String fileUrl,
            SubmissionResponse submission
    ) {}

    record SubmissionResponse(
            String submissionId,
            String submittedAt,
            Integer grade,
            String feedback,
            boolean isLate
    ) {}

    @GetMapping("/my-assignments")
    List<AssignmentResponse> getMyAssignments(
            @RequestHeader("X-Authenticated-User-Id") String userId
    );
}