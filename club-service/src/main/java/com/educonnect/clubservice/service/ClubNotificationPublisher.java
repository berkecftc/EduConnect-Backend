package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubNotificationMessage;
import com.educonnect.clubservice.model.Club;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ClubNotificationPublisher {

    public static final String ROUTING_KEY_CLUB_NOTIFICATION = "club.notification";

    private final OutboxPublisher outboxPublisher;

    public ClubNotificationPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void notifyUser(UUID targetUserId, Club club, String subject, String message) {
        if (targetUserId == null || club == null) {
            return;
        }
        outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_NOTIFICATION,
                new ClubNotificationMessage(targetUserId, club.getId(), club.getName(), subject, message));
    }

    public void notifyUserAboutClubName(UUID targetUserId, String clubName, String subject, String message) {
        if (targetUserId == null) {
            return;
        }
        outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_NOTIFICATION,
                new ClubNotificationMessage(targetUserId, null, clubName, subject, message));
    }

    public void notifyAdvisor(Club club, String subject, String message) {
        if (club != null) {
            notifyUser(club.getAcademicAdvisorId(), club, subject, message);
        }
    }
}
