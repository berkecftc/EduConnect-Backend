package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_grades",
        uniqueConstraints = @UniqueConstraint(name = "uq_member_grades_student", columnNames = {"submission_id", "student_id"}))
public class MemberGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal grade;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected MemberGrade() {
    }

    public MemberGrade(UUID submissionId, UUID studentId) {
        this.submissionId = submissionId;
        this.studentId = studentId;
    }

    public void set(BigDecimal grade, String reason, UUID changedBy, Instant changedAt) {
        this.grade = grade;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public UUID getStudentId() { return studentId; }
    public BigDecimal getGrade() { return grade; }
    public String getReason() { return reason; }
    public UUID getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
