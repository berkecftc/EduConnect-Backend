package com.educonnect.postservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptAnswerRequest(
        @NotNull(message = "Cevap olarak seçilecek yorum belirtilmelidir")
        UUID commentId
) {}
