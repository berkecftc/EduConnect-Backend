package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CourseStaffRole;
import jakarta.validation.constraints.NotNull;

public record CourseStaffRoleRequest(@NotNull CourseStaffRole role) {
}
