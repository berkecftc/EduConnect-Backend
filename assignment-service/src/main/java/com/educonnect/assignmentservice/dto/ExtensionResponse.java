package com.educonnect.assignmentservice.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExtensionResponse(UUID studentId, String studentName, String studentNumber, LocalDateTime dueDate,
                                LocalDateTime lateUntil, String reason, UUID grantedBy, Instant grantedAt) {
}
