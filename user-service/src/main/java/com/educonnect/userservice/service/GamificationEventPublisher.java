package com.educonnect.userservice.service;

import com.educonnect.userservice.config.RabbitMQConfig;
import com.educonnect.userservice.dto.message.GamificationActionType;
import com.educonnect.userservice.dto.message.GamificationEventMessage;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
public class GamificationEventPublisher {

    private final OutboxPublisher outboxPublisher;

    public GamificationEventPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void publishProfileCompleted(UUID userId) {
        GamificationEventMessage event = new GamificationEventMessage(
                userId,
                GamificationActionType.PROFILE_COMPLETED,
                "PROFILE_COMPLETED:" + userId,
                OffsetDateTime.now(ZoneId.of("Europe/Istanbul"))
        );

        outboxPublisher.publish(
                RabbitMQConfig.GAMIFICATION_EXCHANGE,
                RabbitMQConfig.GAMIFICATION_PROFILE_COMPLETED_ROUTING_KEY,
                event
        );
    }
}

