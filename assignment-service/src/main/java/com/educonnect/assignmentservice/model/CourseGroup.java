package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_groups",
        uniqueConstraints = @UniqueConstraint(name = "uq_course_groups_name", columnNames = {"group_set_id", "name"}))
public class CourseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_set_id", nullable = false)
    private UUID groupSetId;

    @Column(nullable = false, length = 100)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CourseGroup() {
    }

    public CourseGroup(UUID groupSetId, String name) {
        this.groupSetId = groupSetId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getGroupSetId() { return groupSetId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
}
