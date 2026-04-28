package com.educonnect.llmservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@FeignClient(name = "assignment-service", path = "/api/assignments")
public interface AssignmentServiceClient {

    // Temiz kod prensibi gereği record kullanıyoruz.
    record AssignmentResponse(
            String id,
            String title,
            String courseName,
            String dueDate,
            String status, // PENDING, SUBMITTED, GRADED vb.
            Double grade
    ) {}

    @GetMapping("/user/{userId}")
    List<AssignmentResponse> getUserAssignments(@PathVariable("userId") String userId);
}