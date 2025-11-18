package com.educonnect.clubservice.dto.message;

import java.io.Serializable;
import java.util.UUID;

public class AssignClubRoleMessage implements Serializable {
    private UUID userId;
    private String clubRole; // Örn: "ROLE_CLUB_OFFICIAL"
    private UUID clubId;     // Hangi kulüp için bu rol atandı

    public AssignClubRoleMessage() {
    }

    public AssignClubRoleMessage(UUID userId, String clubRole, UUID clubId) {
        this.userId = userId;
        this.clubRole = clubRole;
        this.clubId = clubId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getClubRole() {
        return clubRole;
    }

    public void setClubRole(String clubRole) {
        this.clubRole = clubRole;
    }

    public UUID getClubId() {
        return clubId;
    }

    public void setClubId(UUID clubId) {
        this.clubId = clubId;
    }
}
