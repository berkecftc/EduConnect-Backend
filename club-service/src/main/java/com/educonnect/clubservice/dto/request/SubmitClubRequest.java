package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class SubmitClubRequest {
    @NotBlank(message = "Kulüp adı zorunludur")
    @Size(max = 255, message = "Kulüp adı en fazla 255 karakter olabilir")
    private String name;
    private String about;
    @NotNull(message = "Danışman akademisyen zorunludur")
    private UUID academicAdvisorId;

    // Getter/Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
}