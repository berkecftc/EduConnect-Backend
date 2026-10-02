package com.educonnect.gamificationservice.service;

import com.educonnect.gamificationservice.client.UserServiceClient;
import com.educonnect.gamificationservice.client.dto.UserProfileClientResponse;
import com.educonnect.gamificationservice.dto.event.GamificationEvent;
import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.PointHistory;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.repository.PointHistoryRepository;
import com.educonnect.gamificationservice.repository.UserBadgeRepository;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationServiceTest {

    private UserReputationRepository userReputationRepository;
    private PointHistoryRepository pointHistoryRepository;
    private UserServiceClient userServiceClient;
    private UserBadgeRepository userBadgeRepository;
    private GamificationService gamificationService;

    @BeforeEach
    void setUp() {
        userReputationRepository = mock(UserReputationRepository.class);
        pointHistoryRepository = mock(PointHistoryRepository.class);
        userServiceClient = mock(UserServiceClient.class);
        userBadgeRepository = mock(UserBadgeRepository.class);
        when(userBadgeRepository.findByUserIdOrderByEarnedAtAsc(any())).thenReturn(List.of());
        gamificationService = new GamificationService(
                userReputationRepository,
                pointHistoryRepository,
                userServiceClient,
                new NoOpTransactionManager(),
                userBadgeRepository
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
    void notesEarnPointsWhenOthersSaveThem() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(20);

        GamificationEvent event = new GamificationEvent(userId, ActionType.NOTE_SAVED, noteId + ":saver", OffsetDateTime.now());
        event.setContentId(noteId);
        when(userReputationRepository.findById(userId)).thenReturn(Optional.of(reputation));

        gamificationService.processEvent(event);

        ArgumentCaptor<UserReputation> reputationCaptor = ArgumentCaptor.forClass(UserReputation.class);
        verify(userReputationRepository).saveAndFlush(reputationCaptor.capture());
        assertEquals(23, reputationCaptor.getValue().getTotalPoints());

        ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).saveAndFlush(historyCaptor.capture());
        assertEquals(3, historyCaptor.getValue().getPointsEarned());
        assertEquals(noteId, historyCaptor.getValue().getContentId());
    }

    @Test
    void contributionsExtendTheWeeklyStreakWhileLoginsEarnNothing() {
        UUID userId = UUID.randomUUID();
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(40);
        reputation.setCurrentStreak(2);
        reputation.setHighestStreak(2);
        reputation.setLastContributionWeek(LocalDate.of(2026, 3, 16));
        when(userReputationRepository.findById(userId)).thenReturn(Optional.of(reputation));

        gamificationService.processEvent(new GamificationEvent(userId, ActionType.DAILY_LOGIN,
                "LOGIN:2026-03-24:" + userId, OffsetDateTime.of(2026, 3, 24, 8, 30, 0, 0, ZoneOffset.UTC)));
        verify(userReputationRepository, never()).saveAndFlush(any(UserReputation.class));

        gamificationService.processEvent(new GamificationEvent(userId, ActionType.POST_PUBLISHED, "post-42",
                OffsetDateTime.of(2026, 3, 24, 8, 30, 0, 0, ZoneOffset.UTC)));

        ArgumentCaptor<UserReputation> reputationCaptor = ArgumentCaptor.forClass(UserReputation.class);
        verify(userReputationRepository).saveAndFlush(reputationCaptor.capture());
        assertEquals(40, reputationCaptor.getValue().getTotalPoints());
        assertEquals(3, reputationCaptor.getValue().getCurrentStreak());
        assertEquals(3, reputationCaptor.getValue().getHighestStreak());
        assertEquals(LocalDate.of(2026, 3, 23), reputationCaptor.getValue().getLastContributionWeek());
    }

    @Test
    void removedContentLosesItsPointsAndGetsThemBackWhenRestored() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(50);
        when(userReputationRepository.findById(userId)).thenReturn(Optional.of(reputation));
        when(pointHistoryRepository.findByUserIdAndContentId(userId, noteId))
                .thenReturn(List.of(history(ActionType.NOTE_LIKED, 2), history(ActionType.NOTE_SAVED, 3)));

        GamificationEvent reversal = new GamificationEvent(userId, ActionType.POINTS_REVERSED, "revoke:1", OffsetDateTime.now());
        reversal.setContentId(noteId);
        gamificationService.processEvent(reversal);
        assertEquals(45, reputation.getTotalPoints());

        when(pointHistoryRepository.findByUserIdAndContentId(userId, noteId)).thenReturn(List.of(
                history(ActionType.NOTE_LIKED, 2), history(ActionType.NOTE_SAVED, 3), history(ActionType.POINTS_REVERSED, -5)));
        GamificationEvent restore = new GamificationEvent(userId, ActionType.POINTS_RESTORED, "restore:1", OffsetDateTime.now());
        restore.setContentId(noteId);
        gamificationService.processEvent(restore);
        assertEquals(50, reputation.getTotalPoints());
    }

    private static PointHistory history(ActionType type, int points) {
        PointHistory row = new PointHistory();
        row.setActionType(type);
        row.setPointsEarned(points);
        return row;
    }

    @Test
    void shouldRecordZeroPointsWhenDailyLimitReachedForAction() {
        UUID userId = UUID.randomUUID();
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(120);

        GamificationEvent event = new GamificationEvent(
                userId,
                ActionType.NOTE_LIKED,
                "post-999:liker",
                OffsetDateTime.now()
        );

        when(pointHistoryRepository.existsByUserIdAndActionTypeAndReferenceId(userId, ActionType.NOTE_LIKED, "post-999:liker"))
                .thenReturn(false);
        when(pointHistoryRepository.countByUserIdAndActionTypeAndCreatedAtBetweenAndPointsEarnedGreaterThan(
                eq(userId),
                eq(ActionType.NOTE_LIKED),
                any(),
                any(),
                eq(0)
        )).thenReturn(30L);
        when(userReputationRepository.findById(userId)).thenReturn(Optional.of(reputation));

        gamificationService.processEvent(event);

        ArgumentCaptor<UserReputation> reputationCaptor = ArgumentCaptor.forClass(UserReputation.class);
        verify(userReputationRepository).saveAndFlush(reputationCaptor.capture());
        assertEquals(120, reputationCaptor.getValue().getTotalPoints());

        ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).saveAndFlush(historyCaptor.capture());
        assertEquals(0, historyCaptor.getValue().getPointsEarned());
    }

    @Test
    void shouldAddProfileCompletedPoints() {
        UUID userId = UUID.randomUUID();
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(10);

        GamificationEvent event = new GamificationEvent(
                userId,
                ActionType.PROFILE_COMPLETED,
                "PROFILE_COMPLETED:" + userId,
                OffsetDateTime.now()
        );

        when(pointHistoryRepository.existsByUserIdAndActionTypeAndReferenceId(
                userId,
                ActionType.PROFILE_COMPLETED,
                "PROFILE_COMPLETED:" + userId
        )).thenReturn(false);
        when(pointHistoryRepository.countByUserIdAndActionTypeAndCreatedAtBetweenAndPointsEarnedGreaterThan(
                eq(userId),
                eq(ActionType.PROFILE_COMPLETED),
                any(),
                any(),
                eq(0)
        )).thenReturn(0L);
        when(userReputationRepository.findById(userId)).thenReturn(Optional.of(reputation));

        gamificationService.processEvent(event);

        ArgumentCaptor<UserReputation> reputationCaptor = ArgumentCaptor.forClass(UserReputation.class);
        verify(userReputationRepository).saveAndFlush(reputationCaptor.capture());
        assertEquals(30, reputationCaptor.getValue().getTotalPoints());

        ArgumentCaptor<PointHistory> historyCaptor = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).saveAndFlush(historyCaptor.capture());
        assertEquals(20, historyCaptor.getValue().getPointsEarned());
    }

    @Test
    void shouldReturnLeaderboardWithRankOrder() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        UserReputation first = UserReputation.initialize(firstId);
        first.setTotalPoints(250);
        first.setCurrentStreak(3);

        UserReputation second = UserReputation.initialize(secondId);
        second.setTotalPoints(180);
        second.setCurrentStreak(5);

        when(userReputationRepository.findByOrderByTotalPointsDescUserIdAsc(any()))
                .thenReturn(List.of(first, second));

        UserProfileClientResponse firstProfile = new UserProfileClientResponse();
        firstProfile.setFirstName("Ali");
        firstProfile.setLastName("Yilmaz");
        when(userServiceClient.getProfileById(firstId)).thenReturn(firstProfile);

        UserProfileClientResponse secondProfile = new UserProfileClientResponse();
        secondProfile.setFirstName("Ayse");
        secondProfile.setLastName("Demir");
        when(userServiceClient.getProfileById(secondId)).thenReturn(secondProfile);

        var leaderboard = gamificationService.getLeaderboard(2);

        assertEquals(2, leaderboard.size());
        assertEquals(1, leaderboard.get(0).rank());
        assertEquals("Ali Yilmaz", leaderboard.get(0).fullName());
        assertEquals(250, leaderboard.get(0).totalPoints());
        assertEquals(2, leaderboard.get(1).rank());
        assertEquals("Ayse Demir", leaderboard.get(1).fullName());
    }

    @Test
    void shouldRejectInvalidLeaderboardLimit() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gamificationService.getLeaderboard(0)
        );

        assertEquals("Leaderboard limit 1 ile 100 arasinda olmali", exception.getReason());
    }

    @Test
    void shouldReturnFallbackNameWhenUserServiceFails() {
        UUID userId = UUID.randomUUID();

        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(90);
        reputation.setCurrentStreak(2);

        when(userReputationRepository.findByOrderByTotalPointsDescUserIdAsc(any()))
                .thenReturn(List.of(reputation));
        doThrow(new RuntimeException("downstream error")).when(userServiceClient).getProfileById(userId);

        var leaderboard = gamificationService.getLeaderboard(1);

        assertEquals(1, leaderboard.size());
        assertEquals("Bilinmeyen Kullanici", leaderboard.get(0).fullName());
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
