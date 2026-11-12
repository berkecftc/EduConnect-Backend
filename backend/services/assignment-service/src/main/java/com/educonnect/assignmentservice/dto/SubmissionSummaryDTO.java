package com.educonnect.assignmentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SubmissionSummaryDTO {
    private UUID submissionId;
    private UUID studentId;
    private String studentName;
    private String studentNumber;
    private String submissionFileUrl;
    private Instant submittedAt;
    private BigDecimal grade;
    private BigDecimal finalGrade;
    private String textContent;
    private UUID groupId;
    private String groupName;
    private boolean isLate;
    private Boolean aiUsed;
    private String aiNote;
    private String feedback;
    private List<GroupMemberGradeDTO> members;

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

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public BigDecimal getGrade() { return grade; }
    public void setGrade(BigDecimal grade) { this.grade = grade; }
    public BigDecimal getFinalGrade() { return finalGrade; }
    public void setFinalGrade(BigDecimal finalGrade) { this.finalGrade = finalGrade; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }
    public Boolean getAiUsed() { return aiUsed; }
    public void setAiUsed(Boolean aiUsed) { this.aiUsed = aiUsed; }
    public String getAiNote() { return aiNote; }
    public void setAiNote(String aiNote) { this.aiNote = aiNote; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public List<GroupMemberGradeDTO> getMembers() { return members; }
    public void setMembers(List<GroupMemberGradeDTO> members) { this.members = members; }
}
