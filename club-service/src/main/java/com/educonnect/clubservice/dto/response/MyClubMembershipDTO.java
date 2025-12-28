package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubRole;
import java.time.LocalDateTime;
import java.util.UUID;

public class MyClubMembershipDTO {
    private UUID clubId;
    private String clubName;
    private String logoUrl;
    private ClubRole clubRole;
    private boolean isActive;
    private LocalDateTime termStartDate;

    public MyClubMembershipDTO() {}

    // Getters and Setters
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public ClubRole getClubRole() { return clubRole; }
    public void setClubRole(ClubRole clubRole) { this.clubRole = clubRole; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getTermStartDate() { return termStartDate; }
    public void setTermStartDate(LocalDateTime termStartDate) { this.termStartDate = termStartDate; }
}

