package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.model.CourseStatus;

import java.util.UUID;

public record CourseAccessResponse(UUID courseId, CourseStatus status, boolean instructor, boolean enrolled,
                                   CourseStaffRole staffRole) {
}
