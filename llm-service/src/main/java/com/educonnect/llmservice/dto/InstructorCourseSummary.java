package com.educonnect.llmservice.dto;

import java.util.UUID;

public record InstructorCourseSummary(UUID id, String title, String code, String staffRole) {

    public boolean teaches() {
        return staffRole == null || "COORDINATOR".equals(staffRole) || "INSTRUCTOR".equals(staffRole);
    }
}
