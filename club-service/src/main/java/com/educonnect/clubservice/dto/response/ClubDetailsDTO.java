package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ClubDetailsDTO {

    private UUID id;
    private String name;
    private String about;
    private String logoUrl;
    private UUID academicAdvisorId;
    private String advisorName; // Danışman hoca ismi
    private String advisorTitle; // Danışman hoca unvanı
    private Long memberCount; // Toplam üye sayısı
    private List<MemberDTO> members; // Entity yerine DTO listesi
    private ClubStatus status;
    private Instant closedAt;
    private String closureReason;
    private ClubProfileResponse profile;

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
    public String getAdvisorName() { return advisorName; }
    public void setAdvisorName(String advisorName) { this.advisorName = advisorName; }
    public String getAdvisorTitle() { return advisorTitle; }
    public void setAdvisorTitle(String advisorTitle) { this.advisorTitle = advisorTitle; }
    public Long getMemberCount() { return memberCount; }
    public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }
    public List<MemberDTO> getMembers() { return members; }
    public void setMembers(List<MemberDTO> members) { this.members = members; }
    public ClubStatus getStatus() { return status; }
    public void setStatus(ClubStatus status) { this.status = status; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }
    public ClubProfileResponse getProfile() { return profile; }
    public void setProfile(ClubProfileResponse profile) { this.profile = profile; }
}
