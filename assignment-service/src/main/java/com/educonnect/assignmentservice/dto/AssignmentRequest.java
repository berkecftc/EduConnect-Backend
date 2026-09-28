package com.educonnect.assignmentservice.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public class AssignmentRequest {
    @NotBlank(message = "Ödev başlığı boş olamaz")
    @Size(max = 255, message = "Ödev başlığı en fazla 255 karakter olabilir")
    private String title;
    private String description;
    @NotNull(message = "Son teslim tarihi zorunludur")
    private LocalDateTime dueDate;
    @NotNull(message = "Ders ID boş olamaz")
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