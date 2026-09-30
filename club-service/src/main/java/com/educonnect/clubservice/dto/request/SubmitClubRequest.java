package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public class SubmitClubRequest {
    @NotBlank(message = "Kulüp adı zorunludur")
    @Size(max = 255, message = "Kulüp adı en fazla 255 karakter olabilir")
    private String name;
    private String about;
    @NotNull(message = "Danışman akademisyen zorunludur")
    private UUID academicAdvisorId;
    @Size(max = 50, message = "En fazla 50 kurucu eklenebilir")
    private Set<UUID> founderIds;

    // Getter/Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
    public Set<UUID> getFounderIds() { return founderIds; }
    public void setFounderIds(Set<UUID> founderIds) { this.founderIds = founderIds; }
}
