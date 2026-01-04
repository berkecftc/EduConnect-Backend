package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "club_membership_requests", schema = "club_db",
        uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "student_id"}))
public class ClubMembershipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipRequestStatus status = MembershipRequestStatus.PENDING;

    @Column(name = "request_date", nullable = false)
    private LocalDateTime requestDate;

    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    @Column(name = "processed_by")
    private UUID processedBy; // İşlemi yapan yetkili (Kulüp başkanı)

    @Column(name = "message", columnDefinition = "TEXT")
    private String message; // Öğrencinin başvuru mesajı (opsiyonel)

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Red nedeni (opsiyonel)

    // JPA için no-args constructor
    public ClubMembershipRequest() {}

    // Convenience constructor
    public ClubMembershipRequest(UUID clubId, UUID studentId) {
        this.clubId = clubId;
        this.studentId = studentId;
        this.status = MembershipRequestStatus.PENDING;
        this.requestDate = LocalDateTime.now();
    }

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public MembershipRequestStatus getStatus() { return status; }
    public void setStatus(MembershipRequestStatus status) { this.status = status; }

    public LocalDateTime getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDateTime requestDate) { this.requestDate = requestDate; }

    public LocalDateTime getProcessedDate() { return processedDate; }
    public void setProcessedDate(LocalDateTime processedDate) { this.processedDate = processedDate; }

    public UUID getProcessedBy() { return processedBy; }
    public void setProcessedBy(UUID processedBy) { this.processedBy = processedBy; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

