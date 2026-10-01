package com.educonnect.courseservice.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_staff",
        uniqueConstraints = @UniqueConstraint(name = "uq_course_staff_member", columnNames = {"course_id", "user_id"}))
public class CourseStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CourseStaffRole role;

    @Column(name = "added_by")
    private UUID addedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseStaff() {
    }

    public CourseStaff(UUID courseId, UUID userId, CourseStaffRole role, UUID addedBy) {
        if (!role.assignable()) {
            throw new IllegalArgumentException("Coordinator is held by the course itself");
        }
        this.courseId = courseId;
        this.userId = userId;
        this.role = role;
        this.addedBy = addedBy;
    }

    public UUID getId() { return id; }
    public UUID getCourseId() { return courseId; }
    public UUID getUserId() { return userId; }
    public CourseStaffRole getRole() { return role; }
    public UUID getAddedBy() { return addedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void changeRole(CourseStaffRole role) {
        if (!role.assignable()) {
            throw new IllegalArgumentException("Coordinator is held by the course itself");
        }
        this.role = role;
    }
}
