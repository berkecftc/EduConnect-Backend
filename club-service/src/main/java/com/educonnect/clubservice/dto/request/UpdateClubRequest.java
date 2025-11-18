package com.educonnect.clubservice.dto.request;

import java.util.UUID;

public class UpdateClubRequest {
    // Sadece güncellenmesine izin verdiğimiz alanlar
    private String name;
    private String about;
    private UUID academicAdvisorId;

    // --- Getter ve Setter metotları ---
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
}