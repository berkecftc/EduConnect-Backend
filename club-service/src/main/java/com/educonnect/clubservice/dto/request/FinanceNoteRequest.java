package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.Size;

public record FinanceNoteRequest(@Size(max = 5000) String financeNote) {
}
