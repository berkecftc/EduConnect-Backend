package com.educonnect.assignmentservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class MySubmissionDTO {
    private UUID submissionId;
    private LocalDateTime submittedAt;
    private Integer grade;
    private String feedback;
    private boolean isLate;

    public MySubmissionDTO() {}

    // Getters and Setters
    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }
}

