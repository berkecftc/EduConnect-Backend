package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubRole; // Model paketinizdeki Enum'u import edin
import java.util.UUID;

public class MemberDTO {

    private UUID studentId;
    private ClubRole clubRole; // Örn: ROLE_CLUB_OFFICIAL, ROLE_MEMBER

    // V2 (Gelecek Geliştirmesi): user-service'e istek atılarak doldurulabilir
    // private String firstName;
    // private String lastName;
    // private String profileImageUrl;

    // JSON dönüşümü için boş constructor
    public MemberDTO() {}

    public MemberDTO(UUID studentId, ClubRole clubRole) {
        this.studentId = studentId;
        this.clubRole = clubRole;
    }

    // --- Getter ve Setter metotları ---
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public ClubRole getClubRole() { return clubRole; }
    public void setClubRole(ClubRole clubRole) { this.clubRole = clubRole; }
}