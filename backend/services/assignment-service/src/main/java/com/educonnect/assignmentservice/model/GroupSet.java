package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_sets",
        uniqueConstraints = @UniqueConstraint(name = "uq_group_sets_name", columnNames = {"course_id", "name"}))
public class GroupSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "self_signup", nullable = false)
    private boolean selfSignup;

    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "signup_closes_at")
    private LocalDateTime signupClosesAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GroupSet() {
    }

    public GroupSet(UUID courseId, UUID createdBy) {
        this.courseId = courseId;
        this.createdBy = createdBy;
    }

    public boolean signupOpenAt(LocalDateTime at) {
        return selfSignup && (signupClosesAt == null || !at.isAfter(signupClosesAt));
    }

    public UUID getId() { return id; }
    public UUID getCourseId() { return courseId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isSelfSignup() { return selfSignup; }
    public void setSelfSignup(boolean selfSignup) { this.selfSignup = selfSignup; }
    public Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
    public LocalDateTime getSignupClosesAt() { return signupClosesAt; }
    public void setSignupClosesAt(LocalDateTime signupClosesAt) { this.signupClosesAt = signupClosesAt; }
    public UUID getCreatedBy() { return createdBy; }
}
