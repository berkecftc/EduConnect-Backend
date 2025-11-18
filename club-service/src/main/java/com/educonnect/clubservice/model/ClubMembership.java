package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "club_memberships", schema = "club_db",
        uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "student_id"}))
public class ClubMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId; // Hangi kulüp (clubs.id'ye işaret eder)

    @Column(name = "student_id", nullable = false)
    private UUID studentId; // Hangi öğrenci (user_db.students'e işaret eder)

    @Enumerated(EnumType.STRING)
    @Column(name = "club_role", nullable = false)
    private ClubRole clubRole; // Kulüp içindeki görevi (Başkan, Üye, vb.)

    // JPA için no-args constructor
    public ClubMembership() {}

    // Service katmanında kullanılan convenience constructor
    public ClubMembership(UUID clubId, UUID studentId, ClubRole clubRole) {
        this.clubId = clubId;
        this.studentId = studentId;
        this.clubRole = clubRole;
    }

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public ClubRole getClubRole() { return clubRole; }
    public void setClubRole(ClubRole clubRole) { this.clubRole = clubRole; }
}