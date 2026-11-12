package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grade_changes")
public class GradeChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "old_grade", precision = 6, scale = 2)
    private BigDecimal oldGrade;

    @Column(name = "new_grade", precision = 6, scale = 2)
    private BigDecimal newGrade;

    @Column(name = "feedback_changed", nullable = false)
    private boolean feedbackChanged;

    @Column(name = "after_publication", nullable = false)
    private boolean afterPublication;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected GradeChange() {
    }

    public GradeChange(UUID submissionId, BigDecimal oldGrade, BigDecimal newGrade, boolean feedbackChanged,
                       boolean afterPublication, String reason, UUID changedBy, Instant changedAt) {
        this.submissionId = submissionId;
        this.oldGrade = oldGrade;
        this.newGrade = newGrade;
        this.feedbackChanged = feedbackChanged;
        this.afterPublication = afterPublication;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public BigDecimal getOldGrade() { return oldGrade; }
    public BigDecimal getNewGrade() { return newGrade; }
    public boolean isFeedbackChanged() { return feedbackChanged; }
    public boolean isAfterPublication() { return afterPublication; }
    public String getReason() { return reason; }
    public UUID getChangedBy() { return changedBy; }
    public UUID getStudentId() { return studentId; }

    public GradeChange forMember(UUID studentId) {
        this.studentId = studentId;
        return this;
    }
    public Instant getChangedAt() { return changedAt; }
}
