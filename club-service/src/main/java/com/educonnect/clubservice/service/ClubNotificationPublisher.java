package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubNotificationMessage;
import com.educonnect.clubservice.model.Club;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ClubNotificationPublisher {

    public static final String ROUTING_KEY_CLUB_NOTIFICATION = "club.notification";

    private static final Logger log = LoggerFactory.getLogger(ClubNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ClubNotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void notifyUser(UUID targetUserId, Club club, String subject, String message) {
        if (targetUserId == null || club == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_NOTIFICATION,
                    new ClubNotificationMessage(targetUserId, club.getId(), club.getName(), subject, message));
        } catch (Exception e) {
            log.error("Failed to publish club notification for club {}: {}", club.getId(), e.getMessage());
        }
    }

    public void notifyUserAboutClubName(UUID targetUserId, String clubName, String subject, String message) {
        if (targetUserId == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_NOTIFICATION,
                    new ClubNotificationMessage(targetUserId, null, clubName, subject, message));
        } catch (Exception e) {
            log.error("Failed to publish club notification for {}: {}", clubName, e.getMessage());
        }
    }

    public void notifyAdvisor(Club club, String subject, String message) {
        if (club != null) {
            notifyUser(club.getAcademicAdvisorId(), club, subject, message);
        }
    }
}
