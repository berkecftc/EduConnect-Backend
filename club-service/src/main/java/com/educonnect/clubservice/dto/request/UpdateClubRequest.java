package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public class UpdateClubRequest {
    // Sadece güncellenmesine izin verdiğimiz alanlar
    @Size(max = 255, message = "Kulüp adı en fazla 255 karakter olabilir")
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