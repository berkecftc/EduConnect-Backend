package com.educonnect.gamificationservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectoryProfile(UUID id,
                               String firstName,
                               String lastName,
                               List<String> affiliations,
                               UUID facultyId,
                               String studentStatus) {

    public boolean activeStudent() {
        return affiliations != null && affiliations.contains("STUDENT")
                && (studentStatus == null || "ACTIVE".equals(studentStatus));
    }
}
