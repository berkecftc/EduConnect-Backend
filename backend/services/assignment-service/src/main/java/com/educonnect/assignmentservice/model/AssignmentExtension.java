package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignment_extensions",
        uniqueConstraints = @UniqueConstraint(name = "uq_assignment_extensions_student", columnNames = {"assignment_id", "student_id"}))
public class AssignmentExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(length = 500)
    private String reason;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected AssignmentExtension() {
    }

    public AssignmentExtension(UUID assignmentId, UUID studentId) {
        this.assignmentId = assignmentId;
        this.studentId = studentId;
    }

    public void grant(LocalDateTime dueDate, String reason, UUID grantedBy, Instant grantedAt) {
        this.dueDate = dueDate;
        this.reason = reason;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public UUID getId() { return id; }
    public UUID getAssignmentId() { return assignmentId; }
    public UUID getStudentId() { return studentId; }
    public LocalDateTime getDueDate() { return dueDate; }
    public String getReason() { return reason; }
    public UUID getGrantedBy() { return grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
}
