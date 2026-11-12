package com.educonnect.courseservice.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_enrollment_events")
public class CourseEnrollmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EnrollmentEventType type;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected CourseEnrollmentEvent() {
    }

    public CourseEnrollmentEvent(UUID courseId, UUID studentId, EnrollmentEventType type, UUID actorId, String reason,
                                 Instant occurredAt) {
        this.courseId = courseId;
        this.studentId = studentId;
        this.type = type;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getCourseId() { return courseId; }
    public UUID getStudentId() { return studentId; }
    public EnrollmentEventType getType() { return type; }
    public UUID getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
