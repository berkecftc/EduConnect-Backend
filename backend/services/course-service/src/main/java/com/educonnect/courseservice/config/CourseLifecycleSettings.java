package com.educonnect.courseservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CourseLifecycleSettings {

    private final int archiveAfterDays;
    private final int retentionYears;

    public CourseLifecycleSettings(@Value("${educonnect.course.archive.after-days:30}") int archiveAfterDays,
                                   @Value("${educonnect.course.archive.retention-years:0}") int retentionYears) {
        this.archiveAfterDays = Math.max(0, archiveAfterDays);
        this.retentionYears = Math.max(0, retentionYears);
    }

    public int archiveAfterDays() {
        return archiveAfterDays;
    }

    public int retentionYears() {
        return retentionYears;
    }

    public boolean retainsForever() {
        return retentionYears == 0;
    }
}
