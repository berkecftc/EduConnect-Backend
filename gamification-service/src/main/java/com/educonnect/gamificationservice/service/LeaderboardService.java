package com.educonnect.gamificationservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.gamificationservice.client.StudentDirectoryClient;
import com.educonnect.gamificationservice.client.TermClient;
import com.educonnect.gamificationservice.client.dto.DirectoryProfile;
import com.educonnect.gamificationservice.client.dto.TermWindow;
import com.educonnect.gamificationservice.dto.response.LeaderboardEntryResponse;
import com.educonnect.gamificationservice.dto.response.LeaderboardPreferenceResponse;
import com.educonnect.gamificationservice.dto.response.LeaderboardResponse;
import com.educonnect.gamificationservice.model.DisplayMode;
import com.educonnect.gamificationservice.model.LeaderboardPreference;
import com.educonnect.gamificationservice.model.UserBadge;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.repository.LeaderboardPreferenceRepository;
import com.educonnect.gamificationservice.repository.PointHistoryRepository;
import com.educonnect.gamificationservice.repository.UserBadgeRepository;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    static final int CANDIDATE_POOL = 300;
    static final int MAX_LIMIT = 100;
    private static final Set<String> VIEWER_ROLES = Set.of("ROLE_STUDENT", "ROLE_CLUB_OFFICIAL", "ROLE_ADMIN");

    private final UserReputationRepository reputationRepository;
    private final PointHistoryRepository historyRepository;
    private final UserBadgeRepository badgeRepository;
    private final LeaderboardPreferenceRepository preferenceRepository;
    private final StudentDirectoryClient directoryClient;
    private final TermClient termClient;

    public LeaderboardService(UserReputationRepository reputationRepository,
                              PointHistoryRepository historyRepository,
                              UserBadgeRepository badgeRepository,
                              LeaderboardPreferenceRepository preferenceRepository,
                              StudentDirectoryClient directoryClient,
                              TermClient termClient) {
        this.reputationRepository = reputationRepository;
        this.historyRepository = historyRepository;
        this.badgeRepository = badgeRepository;
        this.preferenceRepository = preferenceRepository;
        this.directoryClient = directoryClient;
        this.termClient = termClient;
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(UUID viewerId, String roles, LeaderboardPeriod period, UUID facultyId, int limit) {
        requireViewer(roles);
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "Liste boyutu 1 ile " + MAX_LIMIT + " arasında olmalı.");
        }
        TermWindow term = period == LeaderboardPeriod.TERM ? currentTerm() : null;
        Map<UUID, Long> points = candidates(term);
        Map<UUID, DirectoryProfile> profiles = profiles(points.keySet());
        Map<UUID, LeaderboardPreference> preferences = preferenceRepository.findAllById(points.keySet()).stream()
                .collect(Collectors.toMap(LeaderboardPreference::getUserId, Function.identity()));

        List<UUID> ranked = points.entrySet().stream()
                .filter(entry -> eligible(profiles.get(entry.getKey()), facultyId))
                .filter(entry -> preferences.getOrDefault(entry.getKey(), LeaderboardPreference.defaults(entry.getKey())).isVisible())
                .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();

        List<UUID> shown = ranked.stream().limit(limit).toList();
        Map<UUID, List<String>> badges = badgeRepository.findByUserIdIn(shown).stream()
                .collect(Collectors.groupingBy(UserBadge::getUserId,
                        Collectors.mapping(badge -> badge.getBadgeType().name(), Collectors.toList())));
        List<LeaderboardEntryResponse> entries = new ArrayList<>(shown.size());
        for (int i = 0; i < shown.size(); i++) {
            UUID userId = shown.get(i);
            LeaderboardPreference preference = preferences.getOrDefault(userId, LeaderboardPreference.defaults(userId));
            entries.add(new LeaderboardEntryResponse(i + 1, displayName(profiles.get(userId), preference.getDisplayMode()),
                    points.get(userId), badges.getOrDefault(userId, List.of()), userId.equals(viewerId)));
        }

        int position = ranked.indexOf(viewerId);
        boolean visible = preferenceRepository.findById(viewerId).map(LeaderboardPreference::isVisible).orElse(true);
        LeaderboardResponse.Standing me = new LeaderboardResponse.Standing(position >= 0 ? position + 1 : null,
                ownPoints(viewerId, term, points), visible);
        return new LeaderboardResponse(period, term != null ? term.label() : null,
                term != null ? term.startsOn() : null, term != null ? term.endsOn() : null, facultyId, entries, me);
    }

    @Transactional(readOnly = true)
    public LeaderboardPreferenceResponse preference(UUID userId) {
        LeaderboardPreference preference = preferenceRepository.findById(userId).orElseGet(() -> LeaderboardPreference.defaults(userId));
        return new LeaderboardPreferenceResponse(preference.isVisible(), preference.getDisplayMode());
    }

    @Transactional
    public LeaderboardPreferenceResponse updatePreference(UUID userId, boolean visible, DisplayMode displayMode) {
        LeaderboardPreference preference = preferenceRepository.findById(userId).orElseGet(() -> new LeaderboardPreference(userId));
        preference.update(visible, displayMode);
        preferenceRepository.save(preference);
        return new LeaderboardPreferenceResponse(preference.isVisible(), preference.getDisplayMode());
    }

    static String displayName(DirectoryProfile profile, DisplayMode mode) {
        String first = profile.firstName() == null ? "" : profile.firstName().strip();
        String last = profile.lastName() == null ? "" : profile.lastName().strip();
        if (mode == DisplayMode.INITIALS) {
            return Arrays.stream((first + " " + last).split("\\s+"))
                    .filter(part -> !part.isEmpty())
                    .map(part -> part.substring(0, 1).toUpperCase(TURKISH) + ".")
                    .collect(Collectors.joining(" "));
        }
        return (first + " " + last).strip();
    }

    private Map<UUID, Long> candidates(TermWindow term) {
        Map<UUID, Long> points = new LinkedHashMap<>();
        if (term == null) {
            for (UserReputation reputation : reputationRepository.findByOrderByTotalPointsDescUserIdAsc(PageRequest.of(0, CANDIDATE_POOL))) {
                if (reputation.getTotalPoints() > 0) {
                    points.put(reputation.getUserId(), reputation.getTotalPoints().longValue());
                }
            }
            return points;
        }
        historyRepository.totalsBetween(term.startsOn().atStartOfDay(), term.endsOn().plusDays(1).atStartOfDay(),
                        PageRequest.of(0, CANDIDATE_POOL))
                .forEach(row -> points.put(row.getUserId(), row.getPoints()));
        return points;
    }

    private long ownPoints(UUID viewerId, TermWindow term, Map<UUID, Long> pool) {
        if (pool.containsKey(viewerId)) {
            return pool.get(viewerId);
        }
        if (term == null) {
            return reputationRepository.findById(viewerId).map(reputation -> reputation.getTotalPoints().longValue()).orElse(0L);
        }
        return 0L;
    }

    private Map<UUID, DirectoryProfile> profiles(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return directoryClient.profiles(userIds).stream()
                    .collect(Collectors.toMap(DirectoryProfile::id, Function.identity(), (left, right) -> left));
        } catch (RuntimeException e) {
            log.error("Student directory lookup failed for the leaderboard: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LEADERBOARD_UNAVAILABLE",
                    "Liderlik tablosu şu anda hazırlanamıyor.");
        }
    }

    private TermWindow currentTerm() {
        try {
            TermWindow term = termClient.currentTerm();
            if (term == null || term.startsOn() == null || term.endsOn() == null) {
                throw noTerm();
            }
            return term;
        } catch (FeignException.Conflict | FeignException.NotFound e) {
            throw noTerm();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Current term lookup failed for the leaderboard: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LEADERBOARD_UNAVAILABLE",
                    "Liderlik tablosu şu anda hazırlanamıyor.");
        }
    }

    private static boolean eligible(DirectoryProfile profile, UUID facultyId) {
        return profile != null && profile.activeStudent() && (facultyId == null || facultyId.equals(profile.facultyId()));
    }

    private static void requireViewer(String roles) {
        boolean allowed = roles != null && Arrays.stream(roles.split(",")).map(String::trim).anyMatch(VIEWER_ROLES::contains);
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LEADERBOARD_STUDENTS_ONLY", "Liderlik tablosu öğrencilere açıktır.");
        }
    }

    private static ApiException noTerm() {
        return new ApiException(HttpStatus.CONFLICT, "NO_TERM", "Tanımlı güncel bir dönem yok; tüm zamanlar sekmesini kullanın.");
    }
}
