package com.educonnect.courseservice.model;

import java.util.EnumSet;
import java.util.Set;

public enum CourseStatus {
    DRAFT,
    OPEN,
    ACTIVE,
    COMPLETED,
    ARCHIVED;

    public static final Set<CourseStatus> RUNNING = EnumSet.of(OPEN, ACTIVE);
    public static final Set<CourseStatus> EDITABLE = EnumSet.of(DRAFT, OPEN, ACTIVE);

    public boolean isRunning() {
        return RUNNING.contains(this);
    }

    public boolean isEditable() {
        return EDITABLE.contains(this);
    }
}
