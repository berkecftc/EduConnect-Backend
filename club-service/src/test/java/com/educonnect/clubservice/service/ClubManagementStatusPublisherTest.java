package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.message.ClubManagementStatusChangedEvent;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubManagementStatusPublisherTest {

    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-24T10:00:00Z");
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ClubAuthorizationService authorizationService = mock(ClubAuthorizationService.class);
    private final ClubManagementStatusPublisher publisher =
            new ClubManagementStatusPublisher(rabbitTemplate, authorizationService, Clock.fixed(now, ZoneOffset.UTC));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesCurrentStatusAsVersionedEvent() {
        when(authorizationService.activeManagementPositionOf(userId))
                .thenReturn(Optional.of(new ClubMembership(UUID.randomUUID(), userId, ClubPosition.TREASURER)));

        publisher.publishCurrentStatus(userId);

        ArgumentCaptor<ClubManagementStatusChangedEvent> event = ArgumentCaptor.forClass(ClubManagementStatusChangedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("user-exchange"), eq(ClubManagementStatusPublisher.ROUTING_KEY), event.capture());
        assertThat(event.getValue().userId()).isEqualTo(userId);
        assertThat(event.getValue().managesClub()).isTrue();
        assertThat(event.getValue().occurredAt()).isEqualTo(now);
        assertThat(event.getValue().eventType()).isEqualTo(ClubManagementStatusChangedEvent.EVENT_TYPE);
        assertThat(event.getValue().schemaVersion()).isEqualTo(1);
        assertThat(event.getValue().eventId()).isNotNull();
    }

    @Test
    void waitsForCommitInsideTransaction() {
        when(authorizationService.activeManagementPositionOf(userId)).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishCurrentStatus(userId);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(rabbitTemplate).convertAndSend(eq("user-exchange"), eq(ClubManagementStatusPublisher.ROUTING_KEY), any(Object.class));
    }
}
