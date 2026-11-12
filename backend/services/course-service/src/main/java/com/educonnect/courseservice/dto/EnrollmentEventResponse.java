package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.EnrollmentEventType;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentEventResponse(UUID id, UUID courseId, String courseCode, String courseTitle, String termLabel,
                                      UUID studentId, String studentName, String studentNumber,
                                      EnrollmentEventType type, UUID actorId, String reason, Instant occurredAt,
                                      boolean afterEnrollmentPeriod) {
}
