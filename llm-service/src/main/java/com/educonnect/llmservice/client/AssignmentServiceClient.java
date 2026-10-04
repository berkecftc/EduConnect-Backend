package com.educonnect.llmservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "assignment-service", path = "/api/assignments")
public interface AssignmentServiceClient {

    record AssignmentResponse(
            String id,
            String title,
            String description,
            String dueDate,
            String courseId,
            String fileUrl,
            String type,
            String aiPolicy,
            BigDecimal latePenaltyPercent,
            String effectiveDueDate,
            String effectiveLateUntil,
            SubmissionResponse submission
    ) {}

    record SubmissionResponse(
            String submissionId,
            String submittedAt,
            BigDecimal grade,
            String feedback,
            boolean late
    ) {}

    @GetMapping("/my-assignments")
    List<AssignmentResponse> getMyAssignments(
            @RequestHeader("X-Authenticated-User-Id") String userId
    );
}
