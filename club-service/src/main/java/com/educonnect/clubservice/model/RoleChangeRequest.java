package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kulüp görev değişikliği talebi.
 * Kulüp başkanı veya yönetim kurulu üyesi atamalarının danışman onayına sunulması için kullanılır.
 */
@Entity
@Table(name = "role_change_requests", schema = "club_db")
public class RoleChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId; // Göreve atanacak/görevden alınacak öğrenci

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role")
    private ClubRole currentRole; // Şu anki rolü (null ise normal üye veya yeni atama)

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false)
    private ClubRole requestedRole; // Talep edilen yeni rol

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId; // Talebi oluşturan kişi (kulüp başkanı veya YK üyesi)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoleChangeRequestStatus status = RoleChangeRequestStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason; // Reddedilme nedeni

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt; // Onay/red tarihi

    @Column(name = "processed_by")
    private UUID processedBy; // Onay/red yapan danışman ID'si

    // JPA için no-args constructor
    public RoleChangeRequest() {}

    // Convenience constructor
    public RoleChangeRequest(UUID clubId, UUID studentId, ClubRole currentRole,
                              ClubRole requestedRole, UUID requesterId) {
        this.clubId = clubId;
        this.studentId = studentId;
        this.currentRole = currentRole;
        this.requestedRole = requestedRole;
        this.requesterId = requesterId;
        this.status = RoleChangeRequestStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public ClubRole getCurrentRole() { return currentRole; }
    public void setCurrentRole(ClubRole currentRole) { this.currentRole = currentRole; }

    public ClubRole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(ClubRole requestedRole) { this.requestedRole = requestedRole; }

    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }

    public RoleChangeRequestStatus getStatus() { return status; }
    public void setStatus(RoleChangeRequestStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public UUID getProcessedBy() { return processedBy; }
    public void setProcessedBy(UUID processedBy) { this.processedBy = processedBy; }
}


