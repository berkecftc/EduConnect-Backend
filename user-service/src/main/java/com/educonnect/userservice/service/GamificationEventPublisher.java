package com.educonnect.userservice.service;

import com.educonnect.userservice.config.RabbitMQConfig;
import com.educonnect.userservice.dto.message.GamificationActionType;
import com.educonnect.userservice.dto.message.GamificationEventMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
public class GamificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public GamificationEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishProfileCompleted(UUID userId) {
        GamificationEventMessage event = new GamificationEventMessage(
                userId,
                GamificationActionType.PROFILE_COMPLETED,
                "PROFILE_COMPLETED:" + userId,
                OffsetDateTime.now(ZoneId.of("Europe/Istanbul"))
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.GAMIFICATION_EXCHANGE,
                RabbitMQConfig.GAMIFICATION_PROFILE_COMPLETED_ROUTING_KEY,
                event
        );
    }
}

