package com.educonnect.assignmentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class MySubmissionDTO {
    private UUID submissionId;
    private Instant submittedAt;
    private BigDecimal grade;
    private BigDecimal finalGrade;
    private String textContent;
    private String feedback;
    private boolean isLate;
    private Boolean aiUsed;
    private String aiNote;

    public MySubmissionDTO() {}

    // Getters and Setters
    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public BigDecimal getGrade() { return grade; }
    public void setGrade(BigDecimal grade) { this.grade = grade; }
    public BigDecimal getFinalGrade() { return finalGrade; }
    public void setFinalGrade(BigDecimal finalGrade) { this.finalGrade = finalGrade; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }
    public Boolean getAiUsed() { return aiUsed; }
    public void setAiUsed(Boolean aiUsed) { this.aiUsed = aiUsed; }
    public String getAiNote() { return aiNote; }
    public void setAiNote(String aiNote) { this.aiNote = aiNote; }
}
