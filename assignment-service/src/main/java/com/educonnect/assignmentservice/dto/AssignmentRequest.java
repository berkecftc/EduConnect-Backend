package com.educonnect.assignmentservice.dto;
import com.educonnect.assignmentservice.model.AssessmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
    private AssessmentType type;
    @DecimalMin(value = "0", message = "Ağırlık negatif olamaz")
    @DecimalMax(value = "100", message = "Ağırlık en fazla 100 olabilir")
    @Digits(integer = 3, fraction = 2, message = "Ağırlık en fazla iki ondalık basamaklı olabilir")
    private BigDecimal weight;
    @DecimalMin(value = "0.01", message = "Azami puan sıfırdan büyük olmalı")
    @DecimalMax(value = "1000", message = "Azami puan en fazla 1000 olabilir")
    @Digits(integer = 4, fraction = 2, message = "Azami puan en fazla iki ondalık basamaklı olabilir")
    private BigDecimal maxPoints;
    private LocalDateTime lateUntil;
    @DecimalMin(value = "0", message = "Kesinti negatif olamaz")
    @DecimalMax(value = "100", message = "Kesinti en fazla 100 olabilir")
    @Digits(integer = 3, fraction = 2, message = "Kesinti en fazla iki ondalık basamaklı olabilir")
    private BigDecimal latePenaltyPercent;

    // Getter & Setter
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public AssessmentType getType() { return type; }
    public void setType(AssessmentType type) { this.type = type; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getMaxPoints() { return maxPoints; }
    public void setMaxPoints(BigDecimal maxPoints) { this.maxPoints = maxPoints; }
    public LocalDateTime getLateUntil() { return lateUntil; }
    public void setLateUntil(LocalDateTime lateUntil) { this.lateUntil = lateUntil; }
    public BigDecimal getLatePenaltyPercent() { return latePenaltyPercent; }
    public void setLatePenaltyPercent(BigDecimal latePenaltyPercent) { this.latePenaltyPercent = latePenaltyPercent; }
}
