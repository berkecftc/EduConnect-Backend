package com.educonnect.assignmentservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class SubmissionSummaryDTO {
    private UUID submissionId;
    private UUID studentId;
    private String studentName;
    private String submissionFileUrl;
    private LocalDateTime submittedAt;
    private Integer grade;
    private boolean isLate;

    public SubmissionSummaryDTO() {}

    // Getters and Setters
    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getSubmissionFileUrl() { return submissionFileUrl; }
    public void setSubmissionFileUrl(String submissionFileUrl) { this.submissionFileUrl = submissionFileUrl; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }
}

