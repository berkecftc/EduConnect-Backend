package com.educonnect.assignmentservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class MyAssignmentDTO {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private UUID courseId;
    private String fileUrl;

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
}

