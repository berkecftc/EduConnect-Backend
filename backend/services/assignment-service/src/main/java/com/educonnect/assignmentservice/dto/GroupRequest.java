package com.educonnect.assignmentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupRequest(@NotBlank(message = "Grup adı boş olamaz") @Size(max = 100) String name) {
}
