package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubRole;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Görev değişikliği talebinin detaylarını döndüren DTO
 */
public class RoleChangeRequestDTO {

    private UUID id;
    private UUID clubId;
    private String clubName;
    private UUID studentId;
    private String studentName; // Ad Soyad
    private ClubRole currentRole;
    private ClubRole requestedRole;
    private UUID requesterId;
    private String requesterName; // Talebi oluşturan kişinin adı
    private RoleChangeRequestStatus status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public RoleChangeRequestDTO() {}

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public ClubRole getCurrentRole() { return currentRole; }
    public void setCurrentRole(ClubRole currentRole) { this.currentRole = currentRole; }

    public ClubRole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(ClubRole requestedRole) { this.requestedRole = requestedRole; }

    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public RoleChangeRequestStatus getStatus() { return status; }
    public void setStatus(RoleChangeRequestStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}

