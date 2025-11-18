package com.educonnect.clubservice.dto.request;

import java.util.UUID;

public class SubmitClubRequest {
    private String name;
    private String about;
    private UUID academicAdvisorId;

    // Getter/Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
}