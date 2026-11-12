package com.educonnect.userservice.dto.request;

import com.educonnect.userservice.models.ProgramLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AcademicUnitRequest(UUID parentId,
                                  @NotBlank(message = "Kod boş olamaz")
                                  @Size(max = 20, message = "Kod en fazla 20 karakter olabilir")
                                  @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Kod yalnız harf, rakam, - ve _ içerebilir") String code,
                                  @NotBlank(message = "Ad boş olamaz") @Size(max = 200, message = "Ad en fazla 200 karakter olabilir") String name,
                                  ProgramLevel level,
                                  @Min(value = 1, message = "Süre en az 1 yıl olmalı") @Max(value = 8, message = "Süre en fazla 8 yıl olabilir") Integer durationYears,
                                  Boolean active) {
}
