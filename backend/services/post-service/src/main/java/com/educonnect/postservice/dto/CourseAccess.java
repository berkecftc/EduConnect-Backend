package com.educonnect.postservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseAccess(UUID courseId,
                           String status,
                           boolean instructor,
                           boolean enrolled,
                           String staffRole,
                           String code,
                           String title,
                           String section) {

    public boolean member() {
        return enrolled || staffRole != null;
    }

    public String displayName() {
        String name = code != null ? code + " " + title : title;
        return section != null && !section.isBlank() ? name + " (Şube " + section + ")" : name;
    }
}
