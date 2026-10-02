package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.MaterialKind;

import java.time.Instant;
import java.util.UUID;

public record MaterialResponse(UUID id, UUID courseId, String title, String description, String section, int sortOrder,
                               MaterialKind kind, String fileName, String linkUrl, boolean visible, UUID createdBy,
                               Instant createdAt, Instant updatedAt) {
}
