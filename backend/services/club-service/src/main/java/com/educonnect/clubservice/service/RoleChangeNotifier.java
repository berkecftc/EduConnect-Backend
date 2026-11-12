package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.Club;
import com.educonnect.common.messaging.notification.NotificationCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class RoleChangeNotifier {

    static final String TYPE = "CLUB_ROLE_CHANGE";

    enum Notice {
        REQUEST("Görev değişikliği talebi"),
        APPROVED("Görev değişikliği onaylandı"),
        REJECTED("Görev değişikliği reddedildi"),
        REVOKED("Göreviniz sonlandırıldı");

        private final String subject;

        Notice(String subject) {
            this.subject = subject;
        }
    }

    private final ClubNotificationPublisher publisher;

    RoleChangeNotifier(ClubNotificationPublisher publisher) {
        this.publisher = publisher;
    }

    void send(UUID targetUserId, Club club, Notice notice, String message) {
        if (targetUserId == null) {
            return;
        }
        publisher.notifyUsers(List.of(targetUserId), club, NotificationCategory.CLUB_MANAGEMENT, TYPE, notice.subject,
                message);
    }
}
