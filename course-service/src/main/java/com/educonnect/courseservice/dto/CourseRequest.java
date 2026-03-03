package com.educonnect.courseservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CourseRequest {
    @NotBlank(message = "Ders başlığı boş olamaz")
    private String title;

    @NotBlank(message = "Ders kodu boş olamaz")
    private String code;

    private String description;

    @Min(value = 1, message = "Kredi en az 1 olmalıdır")
    private int credit;

    private String semester;

    @NotNull(message = "Eğitmen ID boş olamaz")
    private UUID instructorId;

    @Min(value = 1, message = "Kapasite en az 1 olmalıdır")
    private int capacity;

    // Getter & Setter
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
    public UUID getInstructorId() { return instructorId; }
    public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
}