package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_group_members_set_student", columnNames = {"group_set_id", "student_id"}))
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "group_set_id", nullable = false)
    private UUID groupSetId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "added_by")
    private UUID addedBy;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected GroupMember() {
    }

    public GroupMember(CourseGroup group, UUID studentId, UUID addedBy, Instant joinedAt) {
        this.groupId = group.getId();
        this.groupSetId = group.getGroupSetId();
        this.studentId = studentId;
        this.addedBy = addedBy;
        this.joinedAt = joinedAt;
    }

    public void moveTo(CourseGroup group, UUID addedBy, Instant at) {
        this.groupId = group.getId();
        this.addedBy = addedBy;
        this.joinedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public UUID getGroupSetId() { return groupSetId; }
    public UUID getStudentId() { return studentId; }
    public UUID getAddedBy() { return addedBy; }
    public Instant getJoinedAt() { return joinedAt; }
}
