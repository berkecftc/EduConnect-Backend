package com.educonnect.assignmentservice.client;

import java.util.UUID;

public record CourseAccess(UUID courseId, String status, boolean instructor, boolean enrolled, String staffRole) {

    public boolean teaches() {
        return instructor;
    }

    public boolean staff() {
        return instructor || staffRole != null;
    }
}
