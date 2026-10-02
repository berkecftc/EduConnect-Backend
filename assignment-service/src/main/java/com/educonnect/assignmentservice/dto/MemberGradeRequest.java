package com.educonnect.assignmentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MemberGradeRequest(@NotNull(message = "Puan zorunludur")
                                 @DecimalMin(value = "0", message = "Puan negatif olamaz")
                                 @Digits(integer = 4, fraction = 2, message = "Puan en fazla iki ondalık basamaklı olabilir") BigDecimal grade,
                                 @NotBlank(message = "Kişisel puan için gerekçe zorunludur")
                                 @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir") String reason) {
}
