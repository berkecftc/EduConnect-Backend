package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.model.MembershipEndReason;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class MyClubMembershipDTO {
    private UUID clubId;
    private String clubName;
    private String logoUrl;
    private ClubPosition clubRole;
    private boolean isActive;
    private LocalDateTime termStartDate;
    private ClubStatus clubStatus;
    private LocalDate validUntil;
    private LocalDateTime endedAt;
    private MembershipEndReason endReason;

    public MyClubMembershipDTO() {}

    // Getters and Setters
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public ClubPosition getClubRole() { return clubRole; }
    public void setClubRole(ClubPosition clubRole) { this.clubRole = clubRole; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getTermStartDate() { return termStartDate; }
    public void setTermStartDate(LocalDateTime termStartDate) { this.termStartDate = termStartDate; }
    public ClubStatus getClubStatus() { return clubStatus; }
    public void setClubStatus(ClubStatus clubStatus) { this.clubStatus = clubStatus; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public MembershipEndReason getEndReason() { return endReason; }
    public void setEndReason(MembershipEndReason endReason) { this.endReason = endReason; }
}
