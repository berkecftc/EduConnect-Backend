package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.Size;

public record NoteRequest(@Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir") String note) {
}
