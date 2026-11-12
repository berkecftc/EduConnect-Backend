package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "Şikâyet nedeni seçilmelidir")
        ReportReason reason,

        @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir")
        String details
) {}
