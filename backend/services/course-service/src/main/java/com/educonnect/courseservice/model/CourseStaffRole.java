package com.educonnect.courseservice.model;

public enum CourseStaffRole {
    COORDINATOR,
    INSTRUCTOR,
    ASSISTANT;

    public boolean manages() {
        return this == COORDINATOR;
    }

    public boolean teaches() {
        return this == COORDINATOR || this == INSTRUCTOR;
    }

    public boolean assignable() {
        return this != COORDINATOR;
    }
}
