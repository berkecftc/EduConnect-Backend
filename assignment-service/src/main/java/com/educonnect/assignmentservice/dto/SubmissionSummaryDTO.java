package com.educonnect.assignmentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class SubmissionSummaryDTO {
    private UUID submissionId;
    private UUID studentId;
    private String studentName;
    private String studentNumber;
    private String submissionFileUrl;
    private LocalDateTime submittedAt;
    private BigDecimal grade;
    private BigDecimal finalGrade;
    private String textContent;
    private boolean isLate;

    public SubmissionSummaryDTO() {}

    // Getters and Setters
    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }

    public String getSubmissionFileUrl() { return submissionFileUrl; }
    public void setSubmissionFileUrl(String submissionFileUrl) { this.submissionFileUrl = submissionFileUrl; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public BigDecimal getGrade() { return grade; }
    public void setGrade(BigDecimal grade) { this.grade = grade; }
    public BigDecimal getFinalGrade() { return finalGrade; }
    public void setFinalGrade(BigDecimal finalGrade) { this.finalGrade = finalGrade; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }
}
