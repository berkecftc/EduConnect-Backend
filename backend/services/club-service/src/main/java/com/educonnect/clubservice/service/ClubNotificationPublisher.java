package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.Club;
import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class ClubNotificationPublisher {

    static final String TYPE_NOTICE = "CLUB_NOTICE";

    private final OutboxPublisher outboxPublisher;

    public ClubNotificationPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void notifyUser(UUID targetUserId, Club club, String subject, String message) {
        if (targetUserId == null || club == null) {
            return;
        }
        notifyUsers(List.of(targetUserId), club, NotificationCategory.CLUB_MANAGEMENT, TYPE_NOTICE, subject, message);
    }

    public void notifyUsers(Collection<UUID> targetUserIds, Club club, NotificationCategory category, String type,
                            String subject, String message) {
        if (club == null) {
            return;
        }
        publish(targetUserIds, club.getId(), category, type, club.getName() + ": " + subject, message);
    }

    public void notifyUserAboutClubName(UUID targetUserId, String clubName, String subject, String message) {
        if (targetUserId == null) {
            return;
        }
        publish(List.of(targetUserId), null, NotificationCategory.CLUB_MANAGEMENT, TYPE_NOTICE, clubName + ": " + subject,
                message);
    }

    public void notifyAdvisor(Club club, String subject, String message) {
        if (club != null) {
            notifyUser(club.getAcademicAdvisorId(), club, subject, message);
        }
    }

    public void publish(Collection<UUID> targetUserIds, UUID clubId, NotificationCategory category, String type,
                        String title, String body) {
        if (targetUserIds == null) {
            return;
        }
        List<UUID> recipients = targetUserIds.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            return;
        }
        outboxPublisher.publish(NotificationRequest.EXCHANGE, NotificationRequest.ROUTING_KEY,
                NotificationRequest.of(recipients, category, type, title, body == null ? "" : body,
                        clubId != null ? "/clubs/" + clubId : null, null));
    }
}
