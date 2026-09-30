package com.educonnect.courseservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CourseRequest {
    @NotBlank(message = "Ders başlığı boş olamaz")
    @Size(max = 255, message = "Ders başlığı en fazla 255 karakter olabilir")
    private String title;

    @NotBlank(message = "Ders kodu boş olamaz")
    @Size(max = 255, message = "Ders kodu en fazla 255 karakter olabilir")
    private String code;

    private String description;

    @Min(value = 1, message = "Kredi en az 1 olmalıdır")
    private int credit;

    @Size(max = 255, message = "Dönem en fazla 255 karakter olabilir")
    private String semester;

    @NotNull(message = "Eğitmen ID boş olamaz")
    private UUID instructorId;

    @Min(value = 1, message = "Kapasite en az 1 olmalıdır")
    private int capacity;

    private UUID termId;

    @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "Şube en fazla 10 harf veya rakam olabilir")
    private String section;

    @Min(value = 0, message = "AKTS negatif olamaz")
    @Max(value = 60, message = "AKTS en fazla 60 olabilir")
    private Integer ects;

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
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public Integer getEcts() { return ects; }
    public void setEcts(Integer ects) { this.ects = ects; }
}
