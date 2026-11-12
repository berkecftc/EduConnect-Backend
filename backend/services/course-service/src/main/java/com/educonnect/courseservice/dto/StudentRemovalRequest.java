package com.educonnect.courseservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentRemovalRequest(@NotBlank @Size(max = 500) String reason) {
}
