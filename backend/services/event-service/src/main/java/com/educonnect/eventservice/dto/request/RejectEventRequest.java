package com.educonnect.eventservice.dto.request;

import jakarta.validation.constraints.Size;

public record RejectEventRequest(@Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir") String reason) {
}
