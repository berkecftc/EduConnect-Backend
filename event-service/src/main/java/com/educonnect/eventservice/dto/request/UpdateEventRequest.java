package com.educonnect.eventservice.dto.request;

import com.educonnect.eventservice.model.AdmissionMode;
import com.educonnect.eventservice.model.EventAudience;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateEventRequest(
        @Size(max = 255, message = "Başlık en fazla 255 karakter olabilir") String title,
        String description,
        @Size(max = 255, message = "Konum en fazla 255 karakter olabilir") String location,
        @Size(max = 2000, message = "Konuşmacı bilgisi en fazla 2000 karakter olabilir") String speakers,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        EventAudience audience,
        AdmissionMode admission,
        @Positive(message = "Kontenjan pozitif olmalı") Integer capacity,
        LocalDateTime registrationOpensAt,
        LocalDateTime registrationClosesAt,
        LocalDateTime cancelUntil,
        @Size(max = 1000, message = "Not en fazla 1000 karakter olabilir") String note) {
}
