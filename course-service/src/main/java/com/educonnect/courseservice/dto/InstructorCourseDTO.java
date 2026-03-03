package com.educonnect.courseservice.dto;

import java.util.UUID;

public class InstructorCourseDTO {
    private UUID id;
    private String title;
    private String code;
    private String description;
    private int credit;
    private String semester;
    private String imageUrl;
    private long enrolledStudentCount;
    private int capacity;
    private long pendingApplicationCount;

    public InstructorCourseDTO() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCredit() { return credit; }
    public void setCredit(int credit) { this.credit = credit; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public long getEnrolledStudentCount() { return enrolledStudentCount; }
    public void setEnrolledStudentCount(long enrolledStudentCount) { this.enrolledStudentCount = enrolledStudentCount; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public long getPendingApplicationCount() { return pendingApplicationCount; }
    public void setPendingApplicationCount(long pendingApplicationCount) { this.pendingApplicationCount = pendingApplicationCount; }
}

