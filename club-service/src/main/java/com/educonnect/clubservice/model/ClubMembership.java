package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.AbstractAggregateRoot;

@Entity
@Table(name = "club_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "student_id"}))
public class ClubMembership extends AbstractAggregateRoot<ClubMembership> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "club_id", nullable = false)
    private UUID clubId; // Hangi kulüp (clubs.id'ye işaret eder)

    @Column(name = "student_id", nullable = false)
    private UUID studentId; // Hangi öğrenci (user_db.students'e işaret eder)

    @Enumerated(EnumType.STRING)
    @Column(name = "club_role", nullable = false)
    private ClubPosition clubRole; // Kulüp içindeki görevi (Başkan, Üye, vb.)

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // Aktif üyelik durumu

    @Column(name = "term_start_date")
    private LocalDateTime termStartDate; // Göreve başlama tarihi (özellikle başkanlar için)

    @Column(name = "term_end_date")
    private LocalDateTime termEndDate; // Görev bitiş tarihi (pasif başkanlar için)

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private MembershipEndReason endReason;

    // JPA için no-args constructor
    public ClubMembership() {}

    // Service katmanında kullanılan convenience constructor
    public ClubMembership(UUID clubId, UUID studentId, ClubPosition clubRole) {
        this.clubId = clubId;
        this.studentId = studentId;
        this.clubRole = clubRole;
        this.isActive = true; // Varsayılan olarak aktif
        if (clubRole != null && clubRole.isManagement()) {
            registerEvent(new PositionChanged(clubId, studentId, null, clubRole, LocalDateTime.now(), null));
        }
    }

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public ClubPosition getClubRole() { return clubRole; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public LocalDateTime getTermStartDate() { return termStartDate; }
    public void setTermStartDate(LocalDateTime termStartDate) { this.termStartDate = termStartDate; }
    public LocalDateTime getTermEndDate() { return termEndDate; }
    public void setTermEndDate(LocalDateTime termEndDate) { this.termEndDate = termEndDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public MembershipEndReason getEndReason() { return endReason; }

    public void end(MembershipEndReason reason, LocalDateTime at) {
        if (clubRole != null && clubRole.isManagement()) {
            registerEvent(new PositionChanged(clubId, studentId, clubRole, ClubPosition.MEMBER, at,
                    positionEndReason(reason)));
            clubRole = ClubPosition.MEMBER;
            termEndDate = at;
        }
        this.isActive = false;
        this.endReason = reason;
        this.endedAt = at;
    }

    private static PositionEndReason positionEndReason(MembershipEndReason reason) {
        return switch (reason) {
            case EXPELLED -> PositionEndReason.EXPELLED;
            case FROZEN -> PositionEndReason.ON_LEAVE;
            case AFFILIATION_ENDED -> PositionEndReason.AFFILIATION_ENDED;
            default -> PositionEndReason.LEFT_CLUB;
        };
    }

    public void assignPosition(ClubPosition position, LocalDateTime at, PositionEndReason reason) {
        ClubPosition previous = this.clubRole;
        if (previous == position) {
            return;
        }
        this.clubRole = position;
        if (position.isManagement()) {
            this.termStartDate = at;
            this.termEndDate = null;
        } else {
            this.termEndDate = at;
        }
        registerEvent(new PositionChanged(clubId, studentId, previous, position, at, reason));
    }

    public void reactivate(LocalDate validUntil, LocalDateTime at) {
        this.clubRole = ClubPosition.MEMBER;
        this.isActive = true;
        this.endReason = null;
        this.endedAt = null;
        this.termStartDate = at;
        this.termEndDate = null;
        this.validUntil = validUntil;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
