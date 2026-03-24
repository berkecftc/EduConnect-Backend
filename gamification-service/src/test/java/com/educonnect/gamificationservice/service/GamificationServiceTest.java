package com.educonnect.gamificationservice.service;

import com.educonnect.gamificationservice.dto.event.GamificationEvent;
import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.PointHistory;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.repository.PointHistoryRepository;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationServiceTest {

    private UserReputationRepository userReputationRepository;
    private PointHistoryRepository pointHistoryRepository;
    private GamificationService gamificationService;

    @BeforeEach
    void setUp() {
        userReputationRepository = mock(UserReputationRepository.class);
        pointHistoryRepository = mock(PointHistoryRepository.class);
        gamificationService = new GamificationService(
                userReputationRepository,
                pointHistoryRepository,
                new NoOpTransactionManager()
        );
    }

    @Test
    void shouldSkipDuplicateEventByIdempotencyKey() {
        UUID userId = UUID.randomUUID();
        GamificationEvent event = new GamificationEvent(userId, ActionType.POST_PUBLISHED, "post-1", OffsetDateTime.now());

        when(pointHistoryRepository.existsByUserIdAndActionTypeAndReferenceId(userId, ActionType.POST_PUBLISHED, "post-1"))
                .thenReturn(true);

        gamificationService.processEvent(event);

        verify(userReputationRepository, never()).saveAndFlush(any(UserReputation.class));
        verify(pointHistoryRepository, never()).saveAndFlush(any(PointHistory.class));
    }

    @Test
    void shouldAddTenPointsForPostPublished() {
        UUID userId = UUID.randomUUID();
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(20);

        GamificationEvent event = new GamificationEvent(userId, ActionType.POST_PUBLISHED, "post-42", OffsetDateTime.now());

        when(pointHistoryRepository.existsByUserIdAndActionTypeAndReferenceId(userId, ActionType.POST_PUBLISHED, "post-42"))
                .thenReturn(false);
        when(userReputationRepository.findById(userId)).thenReturn(Optional.of(reputation));

        gamificationService.processEvent(event);

        ArgumentCaptor<UserReputation> reputationCaptor = ArgumentCaptor.forClass(UserReputation.class);
        verify(userReputationRepository).saveAndFlush(reputationCaptor.capture());
        assertEquals(30, reputationCaptor.getValue().getTotalPoints());

        ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).saveAndFlush(historyCaptor.capture());
        assertEquals(10, historyCaptor.getValue().getPointsEarned());
    }

    @Test
    void shouldResetStreakAndGiveBonusOnSeventhDay() {
        UUID userId = UUID.randomUUID();
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setCurrentStreak(6);
        reputation.setHighestStreak(6);
        reputation.setLastLoginDate(LocalDate.of(2026, 3, 23));

        GamificationEvent event = new GamificationEvent(
                userId,
                ActionType.DAILY_LOGIN,
                "LOGIN:2026-03-24:" + userId,
                OffsetDateTime.of(2026, 3, 24, 8, 30, 0, 0, ZoneOffset.UTC)
        );

        when(pointHistoryRepository.existsByUserIdAndActionTypeAndReferenceId(eq(userId), eq(ActionType.DAILY_LOGIN), anyString()))
                .thenReturn(false);
        when(userReputationRepository.findById(userId)).thenReturn(Optional.of(reputation));

        gamificationService.processEvent(event);

        ArgumentCaptor<UserReputation> reputationCaptor = ArgumentCaptor.forClass(UserReputation.class);
        verify(userReputationRepository).saveAndFlush(reputationCaptor.capture());
        assertEquals(50, reputationCaptor.getValue().getTotalPoints());
        assertEquals(0, reputationCaptor.getValue().getCurrentStreak());
        assertEquals(7, reputationCaptor.getValue().getHighestStreak());

        ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).saveAndFlush(historyCaptor.capture());
        assertEquals(50, historyCaptor.getValue().getPointsEarned());
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}

