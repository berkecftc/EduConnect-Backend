package com.educonnect.notificationservice.dto.request;

import com.educonnect.common.messaging.notification.NotificationCategory;
import jakarta.validation.constraints.NotNull;

public record PreferenceRequest(
        @NotNull(message = "Kategori zorunludur")
        NotificationCategory category,

        @NotNull(message = "E-posta tercihi zorunludur")
        Boolean emailEnabled
) {
}
