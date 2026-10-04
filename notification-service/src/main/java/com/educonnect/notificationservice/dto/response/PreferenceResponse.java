package com.educonnect.notificationservice.dto.response;

import com.educonnect.common.messaging.notification.NotificationCategory;

public record PreferenceResponse(NotificationCategory category, String label, boolean mandatory, boolean emailEnabled) {
}
