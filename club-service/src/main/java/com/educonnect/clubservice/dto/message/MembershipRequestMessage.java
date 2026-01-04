package com.educonnect.clubservice.dto.message;

import java.io.Serializable;
import java.util.UUID;

/**
 * Üyelik isteği işlendiğinde notification-service'e gönderilecek mesaj.
 */
public class MembershipRequestMessage implements Serializable {

    private UUID studentId;
    private UUID clubId;
    private String clubName;
    private String status; // APPROVED, REJECTED, PENDING
    private String message; // Bildirim mesajı

    public MembershipRequestMessage() {}

    public MembershipRequestMessage(UUID studentId, UUID clubId, String clubName, String status, String message) {
        this.studentId = studentId;
        this.clubId = clubId;
        this.clubName = clubName;
        this.status = status;
        this.message = message;
    }

    // Getter/Setter
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

