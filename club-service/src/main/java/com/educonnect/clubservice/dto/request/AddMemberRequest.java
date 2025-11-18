package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ClubRole; // Enum'u import edin
import java.util.UUID;

public class AddMemberRequest {
    private UUID studentId;
    private ClubRole clubRole; // Örn: ROLE_BOARD_MEMBER veya ROLE_MEMBER

    // --- Getter ve Setter metotları ---
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public ClubRole getClubRole() { return clubRole; }
    public void setClubRole(ClubRole clubRole) { this.clubRole = clubRole; }
}