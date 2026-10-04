package com.educonnect.notificationservice.dto.response;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.notificationservice.model.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationCategory category, String type, String title, String body,
                                   String link, boolean read, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getCategory(), notification.getType(),
                notification.getTitle(), notification.getBody(), notification.getLink(), notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
