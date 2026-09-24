package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubManagementStatusChangedEvent;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

@Component
public class ClubManagementStatusPublisher {

    public static final String ROUTING_KEY = "user.club-management.changed";

    private static final Logger log = LoggerFactory.getLogger(ClubManagementStatusPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ClubAuthorizationService clubAuthorizationService;
    private final Clock clock;

    public ClubManagementStatusPublisher(RabbitTemplate rabbitTemplate, ClubAuthorizationService clubAuthorizationService) {
        this(rabbitTemplate, clubAuthorizationService, Clock.systemUTC());
    }

    ClubManagementStatusPublisher(RabbitTemplate rabbitTemplate, ClubAuthorizationService clubAuthorizationService, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clubAuthorizationService = clubAuthorizationService;
        this.clock = clock;
    }

    public void publishCurrentStatus(UUID userId) {
        boolean managesClub = clubAuthorizationService.activeManagementPositionOf(userId).isPresent();
        ClubManagementStatusChangedEvent event = ClubManagementStatusChangedEvent.of(userId, managesClub, clock.instant());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(ClubManagementStatusChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(ClubRabbitMQConfig.USER_EXCHANGE_NAME, ROUTING_KEY, event);
        } catch (Exception e) {
            log.error("Failed to publish club management status for user {}: {}", event.userId(), e.getMessage());
        }
    }
}
