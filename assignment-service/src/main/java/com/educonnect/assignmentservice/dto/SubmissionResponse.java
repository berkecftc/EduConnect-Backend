package com.educonnect.assignmentservice.dto;

import com.educonnect.assignmentservice.model.AssignmentSubmission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record SubmissionResponse(UUID id,
                                 UUID assignmentId,
                                 UUID studentId,
                                 String submissionFileUrl,
                                 LocalDateTime submittedAt,
                                 BigDecimal grade,
                                 String feedback,
                                 boolean late,
                                 Instant createdAt,
                                 Instant updatedAt) {

    public static SubmissionResponse from(AssignmentSubmission submission) {
        return new SubmissionResponse(submission.getId(), submission.getAssignmentId(), submission.getStudentId(),
                submission.getSubmissionFileUrl(), submission.getSubmittedAt(), submission.getGrade(),
                submission.getFeedback(), submission.isLate(), submission.getCreatedAt(), submission.getUpdatedAt());
    }
}
