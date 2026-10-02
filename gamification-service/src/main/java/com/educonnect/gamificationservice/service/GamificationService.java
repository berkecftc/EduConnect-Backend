package com.educonnect.gamificationservice.service;

import com.educonnect.gamificationservice.client.UserServiceClient;
import com.educonnect.gamificationservice.client.dto.UserProfileClientResponse;
import com.educonnect.gamificationservice.dto.event.GamificationEvent;
import com.educonnect.gamificationservice.dto.response.BadgeInfoResponse;
import com.educonnect.gamificationservice.dto.response.GamificationSummaryResponse;
import com.educonnect.gamificationservice.dto.response.LeaderboardEntryResponse;
import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.BadgeType;
import com.educonnect.gamificationservice.model.PointHistory;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.model.UserBadge;
import com.educonnect.gamificationservice.repository.PointHistoryRepository;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import com.educonnect.gamificationservice.repository.UserBadgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GamificationService {

    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);

    private static final Map<ActionType, Integer> POINTS = Map.of(
            ActionType.POST_PUBLISHED, 0,
            ActionType.NOTE_LIKED, 2,
            ActionType.NOTE_SAVED, 3,
            ActionType.ANSWER_ACCEPTED, 15,
            ActionType.VALID_REPORT, 10,
            ActionType.PROFILE_COMPLETED, 20);
    private static final Map<ActionType, Integer> DAILY_LIMITS = Map.of(
            ActionType.POST_PUBLISHED, 3,
            ActionType.NOTE_LIKED, 30,
            ActionType.NOTE_SAVED, 30,
            ActionType.ANSWER_ACCEPTED, 5,
            ActionType.VALID_REPORT, 5,
            ActionType.PROFILE_COMPLETED, 1);
    private static final Set<ActionType> CONTRIBUTIONS = Set.of(
            ActionType.POST_PUBLISHED, ActionType.ANSWER_ACCEPTED, ActionType.VALID_REPORT);
    private static final Set<ActionType> REVISIONS = Set.of(ActionType.POINTS_REVERSED, ActionType.POINTS_RESTORED);

    private static final int STREAK_WEEKS_BRONZE = 3;
    private static final int STREAK_WEEKS_SILVER = 6;
    private static final int STREAK_WEEKS_GOLD = 12;
    private static final int MAX_OPTIMISTIC_RETRIES = 3;
    private static final int MAX_LEADERBOARD_LIMIT = 100;
    private static final String UNKNOWN_USER_DISPLAY_NAME = "Bilinmeyen Kullanici";

    private final UserReputationRepository userReputationRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final UserServiceClient userServiceClient;
    private final TransactionTemplate transactionTemplate;
    private final UserBadgeRepository userBadgeRepository;

    public GamificationService(UserReputationRepository userReputationRepository,
                               PointHistoryRepository pointHistoryRepository,
                               UserServiceClient userServiceClient,
                               PlatformTransactionManager transactionManager,
                               UserBadgeRepository userBadgeRepository) {
        this.userReputationRepository = userReputationRepository;
        this.pointHistoryRepository = pointHistoryRepository;
        this.userServiceClient = userServiceClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.userBadgeRepository = userBadgeRepository;
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
        if (event.getActionType() == ActionType.DAILY_LOGIN) {
            log.debug("Login events no longer earn points. userId={}", event.getUserId());
            return;
        }

        LocalDateTime eventOccurredAt = resolveOccurredAt(event.getOccurredAt());

        if (pointHistoryRepository.existsByUserIdAndActionTypeAndReferenceId(
                event.getUserId(), event.getActionType(), event.getReferenceId())) {
            log.info("Duplicate event skipped by idempotency check. userId={}, actionType={}, referenceId={}",
                    event.getUserId(), event.getActionType(), event.getReferenceId());
            return;
        }

        UserReputation reputation = userReputationRepository.findById(event.getUserId())
                .orElseGet(() -> UserReputation.initialize(event.getUserId()));

        int earnedPoints;
        if (REVISIONS.contains(event.getActionType())) {
            earnedPoints = revision(event);
        } else if (isDailyPointsLimitReached(event.getUserId(), event.getActionType(), eventOccurredAt.toLocalDate())) {
            earnedPoints = 0;
            log.info("Daily points limit reached. userId={}, actionType={}, limit={}",
                    event.getUserId(), event.getActionType(), DAILY_LIMITS.get(event.getActionType()));
        } else {
            earnedPoints = POINTS.getOrDefault(event.getActionType(), 0);
        }
        if (CONTRIBUTIONS.contains(event.getActionType())) {
            applyWeeklyStreak(reputation, eventOccurredAt.toLocalDate());
        }

        reputation.setTotalPoints(Math.max(0, reputation.getTotalPoints() + earnedPoints));
        userReputationRepository.saveAndFlush(reputation);

        PointHistory pointHistory = new PointHistory();
        pointHistory.setUserId(event.getUserId());
        pointHistory.setActionType(event.getActionType());
        pointHistory.setReferenceId(event.getReferenceId());
        pointHistory.setPointsEarned(earnedPoints);
        pointHistory.setCreatedAt(eventOccurredAt);
        pointHistory.setContentId(event.getContentId());
        pointHistoryRepository.saveAndFlush(pointHistory);

        awardNewBadges(event.getUserId(), eventOccurredAt, reputation, event.getActionType());
    }

    private int revision(GamificationEvent event) {
        if (event.getContentId() == null) {
            throw new IllegalArgumentException("Point revision needs a content id");
        }
        List<PointHistory> rows = pointHistoryRepository.findByUserIdAndContentId(event.getUserId(), event.getContentId());
        int net = rows.stream().mapToInt(PointHistory::getPointsEarned).sum();
        int gross = rows.stream()
                .filter(row -> !REVISIONS.contains(row.getActionType()))
                .mapToInt(PointHistory::getPointsEarned)
                .sum();
        int delta = event.getActionType() == ActionType.POINTS_REVERSED ? -Math.max(0, net) : Math.max(0, gross - net);
        log.info("Points revised for content. userId={}, contentId={}, action={}, delta={}",
                event.getUserId(), event.getContentId(), event.getActionType(), delta);
        return delta;
    }

    private void applyWeeklyStreak(UserReputation reputation, LocalDate date) {
        LocalDate week = date.with(DayOfWeek.MONDAY);
        LocalDate last = reputation.getLastContributionWeek();
        if (last == null || last.isBefore(week.minusWeeks(1))) {
            reputation.setCurrentStreak(1);
        } else if (last.isEqual(week.minusWeeks(1))) {
            reputation.setCurrentStreak(reputation.getCurrentStreak() + 1);
        } else if (reputation.getCurrentStreak() == 0) {
            reputation.setCurrentStreak(1);
        }
        if (last == null || !last.isAfter(week)) {
            reputation.setLastContributionWeek(week);
        }
        if (reputation.getCurrentStreak() > reputation.getHighestStreak()) {
            reputation.setHighestStreak(reputation.getCurrentStreak());
        }
    }

    private boolean isDailyPointsLimitReached(UUID userId, ActionType actionType, LocalDate eventDate) {
        LocalDateTime dayStart = eventDate.atStartOfDay();
        LocalDateTime dayEnd = eventDate.plusDays(1).atStartOfDay().minusNanos(1);
        long earnedCount = pointHistoryRepository.countByUserIdAndActionTypeAndCreatedAtBetweenAndPointsEarnedGreaterThan(
                userId,
                actionType,
                dayStart,
                dayEnd,
                0
        );
        return earnedCount >= DAILY_LIMITS.getOrDefault(actionType, 3);
    }

    public int resetInactiveStreaks(LocalDate previousWeek) {
        return transactionTemplate.execute(status -> userReputationRepository.resetInactiveStreaks(previousWeek));
    }

    @Transactional(readOnly = true)
    public GamificationSummaryResponse getUserSummary(UUID userId) {
        UserReputation reputation = userReputationRepository.findById(userId)
                .orElseGet(() -> UserReputation.initialize(userId));

        List<BadgeInfoResponse> badges = userBadgeRepository.findByUserIdOrderByEarnedAtAsc(userId)
                .stream()
                .map(badge -> new BadgeInfoResponse(
                        badge.getBadgeType().name(),
                        badge.getBadgeType().getDisplayName(),
                        badge.getBadgeType().getDescription(),
                        "/api/gamification/badges/" + badge.getBadgeType().name().toLowerCase(Locale.ROOT) + "/image",
                        badge.getEarnedAt()
                ))
                .toList();

        return new GamificationSummaryResponse(
                reputation.getTotalPoints(),
                reputation.getCurrentStreak(),
                reputation.getHighestStreak(),
                badges
        );
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> getLeaderboard(int limit) {
        if (limit <= 0 || limit > MAX_LEADERBOARD_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Leaderboard limit 1 ile " + MAX_LEADERBOARD_LIMIT + " arasinda olmali"
            );
        }

        List<UserReputation> reputations = userReputationRepository.findByOrderByTotalPointsDescUserIdAsc(
                PageRequest.of(0, limit)
        );

        List<LeaderboardEntryResponse> leaderboard = new ArrayList<>(reputations.size());
        for (int i = 0; i < reputations.size(); i++) {
            UserReputation reputation = reputations.get(i);
            leaderboard.add(new LeaderboardEntryResponse(
                    i + 1,
                    resolveDisplayName(reputation.getUserId()),
                    reputation.getTotalPoints(),
                    reputation.getCurrentStreak()
            ));
        }
        return leaderboard;
    }

    private String resolveDisplayName(UUID userId) {
        try {
            UserProfileClientResponse profile = userServiceClient.getProfileById(userId);
            if (profile == null) {
                return UNKNOWN_USER_DISPLAY_NAME;
            }

            String firstName = normalizeName(profile.getFirstName());
            String lastName = normalizeName(profile.getLastName());
            if (firstName == null && lastName == null) {
                return UNKNOWN_USER_DISPLAY_NAME;
            }
            return String.join(" ",
                    Objects.requireNonNullElse(firstName, ""),
                    Objects.requireNonNullElse(lastName, "")
            ).trim();
        } catch (Exception ex) {
            log.warn("User profile could not be resolved for leaderboard. userId={}", userId, ex);
            return UNKNOWN_USER_DISPLAY_NAME;
        }
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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

    private void awardNewBadges(UUID userId, LocalDateTime earnedAt, UserReputation reputation, ActionType actionType) {
        List<BadgeType> eligibleBadges = resolveBadges(reputation.getTotalPoints(), reputation.getHighestStreak(), actionType);
        if (eligibleBadges.isEmpty()) {
            return;
        }

        List<UserBadge> existingBadges = userBadgeRepository.findByUserIdOrderByEarnedAtAsc(userId);
        Set<BadgeType> alreadyEarned = existingBadges.stream()
                .map(UserBadge::getBadgeType)
                .collect(Collectors.toSet());

        List<UserBadge> toSave = new ArrayList<>();
        for (BadgeType badgeType : eligibleBadges) {
            if (alreadyEarned.contains(badgeType)) {
                continue;
            }
            UserBadge userBadge = new UserBadge();
            userBadge.setUserId(userId);
            userBadge.setBadgeType(badgeType);
            userBadge.setEarnedAt(earnedAt);
            toSave.add(userBadge);
        }

        if (!toSave.isEmpty()) {
            userBadgeRepository.saveAll(toSave);
        }
    }

    private List<BadgeType> resolveBadges(int totalPoints, int highestStreak, ActionType actionType) {
        List<BadgeType> badges = new ArrayList<>();

        if (totalPoints >= 1) {
            badges.add(BadgeType.FIRST_STEP);
        }
        if (totalPoints >= 250) {
            badges.add(BadgeType.POINTS_EXPLORER);
        }
        if (totalPoints >= 1000) {
            badges.add(BadgeType.POINTS_MASTER);
        }
        if (highestStreak >= STREAK_WEEKS_BRONZE) {
            badges.add(BadgeType.WEEK_WARRIOR);
        }
        if (highestStreak >= STREAK_WEEKS_SILVER) {
            badges.add(BadgeType.FORTNIGHT_WARRIOR);
        }
        if (highestStreak >= STREAK_WEEKS_GOLD) {
            badges.add(BadgeType.STREAK_LEGEND);
        }
        if (actionType == ActionType.PROFILE_COMPLETED) {
            badges.add(BadgeType.PROFILE_COMPLETE);
        }

        return badges;
    }
}
