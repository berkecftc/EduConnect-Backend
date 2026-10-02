package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;
import com.educonnect.common.storage.ObjectUrlConverter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "assignment_submissions")
public class AssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "submission_file_url")
    @Convert(converter = ObjectUrlConverter.class)
    private String submissionFileUrl; // MinIO'da saklanan teslim dosyasının URL'si

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "grade", precision = 6, scale = 2)
    private BigDecimal grade;

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
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    public String getSubmissionFileUrl() { return submissionFileUrl; }
    public void setSubmissionFileUrl(String submissionFileUrl) { this.submissionFileUrl = submissionFileUrl; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public BigDecimal getGrade() { return grade; }
    public void setGrade(BigDecimal grade) { this.grade = grade; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
