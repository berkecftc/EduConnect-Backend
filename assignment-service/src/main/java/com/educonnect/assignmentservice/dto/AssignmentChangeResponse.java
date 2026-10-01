package com.educonnect.assignmentservice.dto;

import java.time.Instant;
import java.util.UUID;

public record AssignmentChangeResponse(String field, String oldValue, String newValue, UUID changedBy, Instant changedAt) {
}
