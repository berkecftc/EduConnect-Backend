package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ClubRole; // Enum'u import edin

public class UpdateMemberRoleRequest {
    private ClubRole newClubRole; // Yeni rol (örn: ROLE_PRESIDENT)

    // --- Getter ve Setter metotları ---
    public ClubRole getNewClubRole() { return newClubRole; }
    public void setNewClubRole(ClubRole newClubRole) { this.newClubRole = newClubRole; }
}