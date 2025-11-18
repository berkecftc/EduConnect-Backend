package com.educonnect.clubservice.dto.response;

import java.util.List;
import java.util.UUID;

public class ClubDetailsDTO {

    private UUID id;
    private String name;
    private String about;
    private String logoUrl;
    private UUID academicAdvisorId;
    private List<MemberDTO> members; // Entity yerine DTO listesi

    // JSON dönüşümü için boş constructor
    public ClubDetailsDTO() {}

    // --- Getter ve Setter metotları ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
    public List<MemberDTO> getMembers() { return members; }
    public void setMembers(List<MemberDTO> members) { this.members = members; }
}