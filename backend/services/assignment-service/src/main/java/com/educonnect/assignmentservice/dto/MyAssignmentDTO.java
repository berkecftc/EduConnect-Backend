package com.educonnect.assignmentservice.dto;

import com.educonnect.assignmentservice.model.AssessmentType;
import com.educonnect.assignmentservice.model.AiPolicy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class MyAssignmentDTO {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private UUID courseId;
    private String fileUrl;
    private AssessmentType type;
    private AiPolicy aiPolicy;
    private BigDecimal weight;
    private BigDecimal maxPoints;
    private boolean gradesPublished;
    private BigDecimal latePenaltyPercent;
    private LocalDateTime effectiveDueDate;
    private LocalDateTime effectiveLateUntil;
    private UUID groupSetId;
    private UUID groupId;
    private String groupName;

    // Teslim bilgisi (null ise teslim edilmemiş)
    private MySubmissionDTO submission;

    public MyAssignmentDTO() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public MySubmissionDTO getSubmission() { return submission; }
    public void setSubmission(MySubmissionDTO submission) { this.submission = submission; }
    public AiPolicy getAiPolicy() { return aiPolicy; }
    public void setAiPolicy(AiPolicy aiPolicy) { this.aiPolicy = aiPolicy; }
    public AssessmentType getType() { return type; }
    public void setType(AssessmentType type) { this.type = type; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getMaxPoints() { return maxPoints; }
    public void setMaxPoints(BigDecimal maxPoints) { this.maxPoints = maxPoints; }
    public boolean isGradesPublished() { return gradesPublished; }
    public void setGradesPublished(boolean gradesPublished) { this.gradesPublished = gradesPublished; }
    public BigDecimal getLatePenaltyPercent() { return latePenaltyPercent; }
    public void setLatePenaltyPercent(BigDecimal latePenaltyPercent) { this.latePenaltyPercent = latePenaltyPercent; }
    public LocalDateTime getEffectiveDueDate() { return effectiveDueDate; }
    public void setEffectiveDueDate(LocalDateTime effectiveDueDate) { this.effectiveDueDate = effectiveDueDate; }
    public LocalDateTime getEffectiveLateUntil() { return effectiveLateUntil; }
    public void setEffectiveLateUntil(LocalDateTime effectiveLateUntil) { this.effectiveLateUntil = effectiveLateUntil; }
    public UUID getGroupSetId() { return groupSetId; }
    public void setGroupSetId(UUID groupSetId) { this.groupSetId = groupSetId; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}
