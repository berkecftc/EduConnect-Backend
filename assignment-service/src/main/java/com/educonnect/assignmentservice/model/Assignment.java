package com.educonnect.assignmentservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignments", schema = "assignment_db")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime dueDate; // Son teslim tarihi

    @Column(nullable = false)
    private UUID courseId; // Hangi derse ait?

    private String fileUrl; // Hoca ek dosya yüklediyse (MinIO)

    // Lomboksuz Getter/Setter & Constructor
    public Assignment() {}

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
}