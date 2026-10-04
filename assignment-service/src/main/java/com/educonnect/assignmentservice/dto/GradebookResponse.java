package com.educonnect.assignmentservice.dto;

import com.educonnect.assignmentservice.model.AssessmentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GradebookResponse(UUID courseId, BigDecimal totalWeight, List<Column> assessments, List<Row> students) {

    public record Column(UUID id, String title, AssessmentType type, BigDecimal weight, BigDecimal maxPoints,
                         LocalDateTime dueDate, boolean gradesPublished) {
    }

    public record Row(UUID studentId, String studentName, String studentNumber, List<Cell> grades,
                      BigDecimal weightedTotal, BigDecimal gradedWeight, int missing) {
    }

    public record Cell(UUID assignmentId, UUID submissionId, Status status, BigDecimal grade, BigDecimal finalGrade, boolean late,
                       Instant submittedAt) {
    }

    public enum Status {
        NOT_SUBMITTED,
        SUBMITTED,
        GRADED
    }
}
