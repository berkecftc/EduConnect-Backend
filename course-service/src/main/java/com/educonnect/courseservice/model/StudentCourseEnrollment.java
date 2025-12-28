package com.educonnect.courseservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_course_enrollments", schema = "course_db",
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
}

