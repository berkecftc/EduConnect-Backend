package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CourseStaffRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CoordinatorTransferRequest(@NotNull UUID userId, CourseStaffRole previousCoordinatorRole) {
}
