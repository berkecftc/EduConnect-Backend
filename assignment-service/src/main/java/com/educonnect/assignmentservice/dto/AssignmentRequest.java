package com.educonnect.assignmentservice.dto;
import java.time.LocalDateTime;
import java.util.UUID;

public class AssignmentRequest {
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private UUID courseId;

    // Getter & Setter
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
}