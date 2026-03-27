package com.educonnect.gamificationservice.service;

import com.educonnect.gamificationservice.dto.event.GamificationEvent;
import com.educonnect.gamificationservice.dto.response.GamificationSummaryResponse;
import com.educonnect.gamificationservice.model.PointHistory;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.repository.PointHistoryRepository;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GamificationService {

    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);

    private static final int POST_PUBLISHED_POINTS = 10;
    private static final int ANSWER_ACCEPTED_POINTS = 50;
    private static final int VALID_REPORT_POINTS = 15;
    private static final int STREAK_BONUS_POINTS = 50;

    private static final int STREAK_BONUS_THRESHOLD = 7;
    private static final int MAX_OPTIMISTIC_RETRIES = 3;

    private final UserReputationRepository userReputationRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final TransactionTemplate transactionTemplate;

    public GamificationService(UserReputationRepository userReputationRepository,
                               PointHistoryRepository pointHistoryRepository,
                               PlatformTransactionManager transactionManager) {
        this.userReputationRepository = userReputationRepository;
        this.pointHistoryRepository = pointHistoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void processEvent(GamificationEvent event) {
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> processEventInTransaction(event));
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt == MAX_OPTIMISTIC_RETRIES) {
                    throw ex;
                }
                log.warn("Optimistic locking retry. userId={}, actionType={}, attempt={}",
                        event.getUserId(), event.getActionType(), attempt);
            }
        }
    }

    private void processEventInTransaction(GamificationEvent event) {
        validateEvent(event);

        if (pointHistoryRepository.existsByUserIdAndActionTypeAndReferenceId(
                event.getUserId(), event.getActionType(), event.getReferenceId())) {
            log.info("Duplicate event skipped by idempotency check. userId={}, actionType={}, referenceId={}",
                    event.getUserId(), event.getActionType(), event.getReferenceId());
            return;
        }

        UserReputation reputation = userReputationRepository.findById(event.getUserId())
                .orElseGet(() -> UserReputation.initialize(event.getUserId()));

        int earnedPoints = switch (event.getActionType()) {
            case POST_PUBLISHED -> POST_PUBLISHED_POINTS;
            case ANSWER_ACCEPTED -> ANSWER_ACCEPTED_POINTS;
            case VALID_REPORT -> VALID_REPORT_POINTS;
            case DAILY_LOGIN -> applyDailyLoginStreak(reputation, event.getOccurredAt());
        };

        reputation.setTotalPoints(reputation.getTotalPoints() + earnedPoints);
        userReputationRepository.saveAndFlush(reputation);

        PointHistory pointHistory = new PointHistory();
        pointHistory.setUserId(event.getUserId());
        pointHistory.setActionType(event.getActionType());
        pointHistory.setReferenceId(event.getReferenceId());
        pointHistory.setPointsEarned(earnedPoints);
        pointHistory.setCreatedAt(resolveOccurredAt(event.getOccurredAt()));
        pointHistoryRepository.saveAndFlush(pointHistory);
    }

    public int resetInactiveStreaks(LocalDate yesterday) {
        return transactionTemplate.execute(status -> userReputationRepository.resetInactiveStreaks(yesterday));
    }

    @Transactional(readOnly = true)
    public GamificationSummaryResponse getUserSummary(UUID userId) {
        UserReputation reputation = userReputationRepository.findById(userId)
                .orElseGet(() -> UserReputation.initialize(userId));

        List<String> badges = resolveBadges(reputation.getTotalPoints(), reputation.getHighestStreak());
        return new GamificationSummaryResponse(
                reputation.getTotalPoints(),
                reputation.getCurrentStreak(),
                reputation.getHighestStreak(),
                badges
        );
    }

    private int applyDailyLoginStreak(UserReputation reputation, OffsetDateTime occurredAt) {
        LocalDate loginDate = resolveOccurredAt(occurredAt).toLocalDate();

        if (reputation.getLastLoginDate() != null) {
            if (reputation.getLastLoginDate().isEqual(loginDate.minusDays(1))) {
                reputation.setCurrentStreak(reputation.getCurrentStreak() + 1);
            } else if (!reputation.getLastLoginDate().isEqual(loginDate)) {
                reputation.setCurrentStreak(1);
            }
        } else {
            reputation.setCurrentStreak(1);
        }

        int streakBeforeReset = reputation.getCurrentStreak();
        if (streakBeforeReset > reputation.getHighestStreak()) {
            reputation.setHighestStreak(streakBeforeReset);
        }

        int earnedPoints = 0;
        if (streakBeforeReset >= STREAK_BONUS_THRESHOLD) {
            earnedPoints = STREAK_BONUS_POINTS;
            reputation.setCurrentStreak(0);
        }

        reputation.setLastLoginDate(loginDate);
        return earnedPoints;
    }

    private LocalDateTime resolveOccurredAt(OffsetDateTime occurredAt) {
        if (occurredAt != null) {
            return occurredAt.atZoneSameInstant(ZoneId.of("Europe/Istanbul")).toLocalDateTime();
        }
        return OffsetDateTime.now(ZoneId.of("Europe/Istanbul")).toLocalDateTime();
    }

    private void validateEvent(GamificationEvent event) {
        if (event == null || event.getUserId() == null || event.getActionType() == null ||
                event.getReferenceId() == null || event.getReferenceId().isBlank()) {
            throw new IllegalArgumentException("Gamification event validation failed");
        }
    }

    private List<String> resolveBadges(int totalPoints, int highestStreak) {
        List<String> badges = new ArrayList<>();

        if (totalPoints >= 1000) {
            badges.add("POINTS_MASTER");
        }
        if (totalPoints >= 250) {
            badges.add("POINTS_EXPLORER");
        }
        if (highestStreak >= 30) {
            badges.add("STREAK_LEGEND");
        }
        if (highestStreak >= 7) {
            badges.add("WEEK_WARRIOR");
        }

        return badges;
    }
}


