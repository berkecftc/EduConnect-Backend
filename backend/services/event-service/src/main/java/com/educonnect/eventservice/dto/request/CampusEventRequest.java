package com.educonnect.eventservice.dto.request;

import com.educonnect.eventservice.model.AdmissionMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CampusEventRequest(
        @NotBlank(message = "Başlık boş olamaz") @Size(max = 255, message = "Başlık en fazla 255 karakter olabilir") String title,
        String description,
        @NotNull(message = "Etkinlik başlangıcı boş olamaz") @JsonAlias("eventTime") LocalDateTime startsAt,
        LocalDateTime endsAt,
        @Size(max = 255, message = "Konum en fazla 255 karakter olabilir") String location,
        @Size(max = 2000, message = "Konuşmacı bilgisi en fazla 2000 karakter olabilir") String speakers,
        @NotBlank(message = "Düzenleyen birim boş olamaz") @Size(max = 200, message = "Düzenleyen birim en fazla 200 karakter olabilir") String organizerName,
        AdmissionMode admission,
        @Positive(message = "Kontenjan pozitif olmalı") Integer capacity,
        LocalDateTime registrationOpensAt,
        LocalDateTime registrationClosesAt,
        LocalDateTime cancelUntil) {
}
