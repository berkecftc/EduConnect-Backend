package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubManagementStatusChangedEvent;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class ClubManagementStatusPublisher {

    public static final String ROUTING_KEY = "user.club-management.changed";

    private final OutboxPublisher outboxPublisher;
    private final ClubAuthorizationService clubAuthorizationService;
    private final Clock clock;

    @Autowired
    public ClubManagementStatusPublisher(OutboxPublisher outboxPublisher, ClubAuthorizationService clubAuthorizationService) {
        this(outboxPublisher, clubAuthorizationService, Clock.systemUTC());
    }

    ClubManagementStatusPublisher(OutboxPublisher outboxPublisher, ClubAuthorizationService clubAuthorizationService, Clock clock) {
        this.outboxPublisher = outboxPublisher;
        this.clubAuthorizationService = clubAuthorizationService;
        this.clock = clock;
    }

    public void publishCurrentStatus(UUID userId) {
        boolean managesClub = clubAuthorizationService.activeManagementPositionOf(userId).isPresent();
        ClubManagementStatusChangedEvent event = ClubManagementStatusChangedEvent.of(userId, managesClub, clock.instant());
        outboxPublisher.publish(ClubRabbitMQConfig.USER_EXCHANGE_NAME, ROUTING_KEY, event);
    }
}
