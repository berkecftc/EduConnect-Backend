package com.educonnect.clubservice.dto.message;

import java.io.Serializable;
import java.util.UUID;

/**
 * Görev değişikliği bildirimi için RabbitMQ mesaj DTO'su.
 * İlgili öğrenci, kulüp başkanı ve danışmana gönderilecek.
 */
public class RoleChangeNotificationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID targetUserId;      // Bildirimi alacak kullanıcı
    private UUID clubId;
    private String clubName;
    private UUID affectedStudentId; // Görev değişikliği yapılan öğrenci
    private String affectedStudentName;
    private String previousRole;    // Önceki rol
    private String newRole;         // Yeni rol
    private String status;          // PENDING, APPROVED, REJECTED
    private String message;         // Bildirim mesajı
    private String notificationType; // ROLE_CHANGE_REQUEST, ROLE_CHANGE_APPROVED, ROLE_CHANGE_REJECTED, ROLE_REVOKED

    public RoleChangeNotificationMessage() {}

    public RoleChangeNotificationMessage(UUID targetUserId, UUID clubId, String clubName,
                                          UUID affectedStudentId, String affectedStudentName,
                                          String previousRole, String newRole, String status,
                                          String message, String notificationType) {
        this.targetUserId = targetUserId;
        this.clubId = clubId;
        this.clubName = clubName;
        this.affectedStudentId = affectedStudentId;
        this.affectedStudentName = affectedStudentName;
        this.previousRole = previousRole;
        this.newRole = newRole;
        this.status = status;
        this.message = message;
        this.notificationType = notificationType;
    }

    // --- Getter/Setter ---
    public UUID getTargetUserId() { return targetUserId; }
    public void setTargetUserId(UUID targetUserId) { this.targetUserId = targetUserId; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public UUID getAffectedStudentId() { return affectedStudentId; }
    public void setAffectedStudentId(UUID affectedStudentId) { this.affectedStudentId = affectedStudentId; }

    public String getAffectedStudentName() { return affectedStudentName; }
    public void setAffectedStudentName(String affectedStudentName) { this.affectedStudentName = affectedStudentName; }

    public String getPreviousRole() { return previousRole; }
    public void setPreviousRole(String previousRole) { this.previousRole = previousRole; }

    public String getNewRole() { return newRole; }
    public void setNewRole(String newRole) { this.newRole = newRole; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
}

