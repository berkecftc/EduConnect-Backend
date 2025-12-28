package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignment_submissions", schema = "assignment_db",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "student_id"}))
public class AssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "submission_file_url")
    private String submissionFileUrl; // MinIO'da saklanan teslim dosyasının URL'si

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "grade")
    private Integer grade; // 0-100 arası not

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback; // Akademisyenin geri bildirimi

    @Column(name = "is_late", nullable = false)
    private boolean isLate = false; // Geç teslim mi?

    // No-args constructor
    public AssignmentSubmission() {}

    // Constructor for service layer
    public AssignmentSubmission(UUID assignmentId, UUID studentId, String submissionFileUrl, boolean isLate) {
        this.assignmentId = assignmentId;
        this.studentId = studentId;
        this.submissionFileUrl = submissionFileUrl;
        this.submittedAt = LocalDateTime.now();
        this.isLate = isLate;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getSubmissionFileUrl() { return submissionFileUrl; }
    public void setSubmissionFileUrl(String submissionFileUrl) { this.submissionFileUrl = submissionFileUrl; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }
}

