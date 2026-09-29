package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ClubPosition; // Enum'u import edin

public class UpdateMemberRoleRequest {
    private ClubPosition newClubRole; // Yeni rol (örn: ROLE_PRESIDENT)

    // --- Getter ve Setter metotları ---
    public ClubPosition getNewClubRole() { return newClubRole; }
    public void setNewClubRole(ClubPosition newClubRole) { this.newClubRole = newClubRole; }
}