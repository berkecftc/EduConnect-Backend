package com.educonnect.courseservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_course_enrollments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "student_id"}))
public class StudentCourseEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "enrollment_date", nullable = false)
    private LocalDateTime enrollmentDate = LocalDateTime.now();

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;

    @Column(name = "withdrawal_reason", length = 500)
    private String withdrawalReason;

    // No-args constructor
    public StudentCourseEnrollment() {}

    // Constructor for service layer
    public StudentCourseEnrollment(UUID courseId, UUID studentId) {
        this.courseId = courseId;
        this.studentId = studentId;
        this.enrollmentDate = LocalDateTime.now();
        this.isActive = true;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public LocalDateTime getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDateTime enrollmentDate) { this.enrollmentDate = enrollmentDate; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public UUID getWithdrawnBy() { return withdrawnBy; }
    public String getWithdrawalReason() { return withdrawalReason; }

    public void withdraw(UUID actorId, String reason, Instant at) {
        this.isActive = false;
        this.withdrawnAt = at;
        this.withdrawnBy = actorId;
        this.withdrawalReason = reason;
    }

    public void reactivate(LocalDateTime at) {
        this.isActive = true;
        this.enrollmentDate = at;
        this.withdrawnAt = null;
        this.withdrawnBy = null;
        this.withdrawalReason = null;
    }
}
