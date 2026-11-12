package com.educonnect.assignmentservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record GroupSetRequest(@NotBlank(message = "Grup seti adı boş olamaz") @Size(max = 100) String name,
                              Boolean selfSignup,
                              @Min(value = 1, message = "Grup kontenjanı en az 1 olmalı")
                              @Max(value = 100, message = "Grup kontenjanı en fazla 100 olabilir") Integer maxMembers,
                              LocalDateTime signupClosesAt) {
}
