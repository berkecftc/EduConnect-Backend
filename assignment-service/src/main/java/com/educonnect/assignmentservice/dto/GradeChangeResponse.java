package com.educonnect.assignmentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GradeChangeResponse(BigDecimal oldGrade, BigDecimal newGrade, boolean feedbackChanged,
                                  boolean afterPublication, String reason, UUID changedBy, Instant changedAt) {
}
