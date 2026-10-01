package com.educonnect.assignmentservice.dto;

import com.educonnect.assignmentservice.model.AssessmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssignmentUpdateRequest(
        @Size(min = 1, max = 255, message = "Ödev başlığı 1-255 karakter olmalı") String title,
        @Size(max = 10000, message = "Açıklama en fazla 10000 karakter olabilir") String description,
        AssessmentType type,
        @DecimalMin(value = "0", message = "Ağırlık negatif olamaz")
        @DecimalMax(value = "100", message = "Ağırlık en fazla 100 olabilir")
        @Digits(integer = 3, fraction = 2, message = "Ağırlık en fazla iki ondalık basamaklı olabilir") BigDecimal weight,
        @DecimalMin(value = "0.01", message = "Azami puan sıfırdan büyük olmalı")
        @DecimalMax(value = "1000", message = "Azami puan en fazla 1000 olabilir")
        @Digits(integer = 4, fraction = 2, message = "Azami puan en fazla iki ondalık basamaklı olabilir") BigDecimal maxPoints,
        LocalDateTime dueDate) {
}
