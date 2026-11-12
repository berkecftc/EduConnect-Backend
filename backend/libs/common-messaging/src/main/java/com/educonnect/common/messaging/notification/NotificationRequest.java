package com.educonnect.common.messaging.notification;

import java.util.List;
import java.util.UUID;

public record NotificationRequest(
        List<UUID> recipientIds,
        NotificationCategory category,
        String type,
        String title,
        String body,
        String link,
        String dedupKey
) {

    public static final String EXCHANGE = "notification.exchange";
    public static final String ROUTING_KEY = "notification.request";

    public static NotificationRequest of(List<UUID> recipientIds, NotificationCategory category, String type,
                                         String title, String body, String link, String dedupKey) {
        return new NotificationRequest(List.copyOf(recipientIds), category, type, title, body, link, dedupKey);
    }
}
