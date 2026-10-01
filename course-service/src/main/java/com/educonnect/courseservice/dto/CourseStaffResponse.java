package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CourseStaffRole;

import java.time.Instant;
import java.util.UUID;

public record CourseStaffResponse(UUID userId, CourseStaffRole role, String name, String department, Instant since) {
}
