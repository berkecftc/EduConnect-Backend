package com.educonnect.gamificationservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.gamificationservice.client.StudentDirectoryClient;
import com.educonnect.gamificationservice.client.TermClient;
import com.educonnect.gamificationservice.client.dto.DirectoryProfile;
import com.educonnect.gamificationservice.client.dto.TermWindow;
import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.BadgeType;
import com.educonnect.gamificationservice.model.PointHistory;
import com.educonnect.gamificationservice.model.UserBadge;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.repository.PointHistoryRepository;
import com.educonnect.gamificationservice.repository.UserBadgeRepository;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.educonnect.common.messaging.notification.TurkishDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@GamificationIntegrationTest
class LeaderboardTest {

    private static final LocalDate TERM_START = LocalDate.of(2026, 9, 21);
    private static final LocalDate TERM_END = LocalDate.of(2027, 1, 15);

    private final UUID engineering = UUID.randomUUID();
    private final UUID medicine = UUID.randomUUID();
    private final UUID ayse = UUID.randomUUID();
    private final UUID mehmet = UUID.randomUUID();
    private final UUID hoca = UUID.randomUUID();
    private final UUID shy = UUID.randomUUID();
    private final UUID elif = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserReputationRepository reputationRepository;

    @Autowired
    private PointHistoryRepository historyRepository;

    @Autowired
    private UserBadgeRepository badgeRepository;

    @MockitoBean
    private StudentDirectoryClient directoryClient;

    @MockitoBean
    private TermClient termClient;

    @BeforeEach
    void seed() {
        reputation(ayse, 950);
        reputation(mehmet, 940);
        reputation(hoca, 990);
        reputation(shy, 930);
        reputation(elif, 920);
        history(ayse, 5, TERM_START.atTime(10, 0));
        history(ayse, 900, TERM_START.minusDays(30).atTime(10, 0));
        history(mehmet, 35, TERM_END.atTime(23, 0));
        history(elif, 20, TERM_START.plusDays(10).atTime(9, 0));
        badge(ayse, BadgeType.FIRST_STEP);

        Map<UUID, DirectoryProfile> directory = Map.of(
                ayse, student(ayse, "Ayşe", "Yılmaz", engineering),
                mehmet, student(mehmet, "Mehmet", "Demir", medicine),
                hoca, new DirectoryProfile(hoca, "Ali", "Hoca", List.of("ACADEMICIAN"), engineering, null),
                shy, student(shy, "Utangaç", "Öğrenci", engineering),
                elif, student(elif, "elif nur", "yıldız", engineering));
        when(directoryClient.profiles(any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().filter(directory::containsKey).map(directory::get).toList();
        });
        when(termClient.currentTerm()).thenReturn(new TermWindow("2026-2027 Güz", TERM_START, TERM_END));
    }

    @Test
    void allTimeTableListsOnlyVisibleStudentsInTheirChosenNameFormat() throws Exception {
        preference(shy, false, "FULL_NAME");
        preference(elif, true, "INITIALS");

        mockMvc.perform(as(get("/api/gamification/leaderboard").param("period", "ALL_TIME").param("limit", "100"),
                        TestTokens.student(shy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ali Hoca')]").doesNotExist())
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Utangaç Öğrenci')]").doesNotExist())
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ayşe Yılmaz')].points").value(950))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ayşe Yılmaz')].badges[0]").value("FIRST_STEP"))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'E. N. Y.')].points").value(920))
                .andExpect(jsonPath("$.entries[0].currentStreak").doesNotExist())
                .andExpect(jsonPath("$.me.rank").doesNotExist())
                .andExpect(jsonPath("$.me.points").value(930))
                .andExpect(jsonPath("$.me.visible").value(false));
    }

    @Test
    void termTableCountsOnlyPointsEarnedInTheCurrentTermAndFiltersByFaculty() throws Exception {
        mockMvc.perform(as(get("/api/gamification/leaderboard").param("limit", "100"), TestTokens.student(ayse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("TERM"))
                .andExpect(jsonPath("$.termLabel").value("2026-2027 Güz"))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Mehmet Demir')].points").value(35))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ayşe Yılmaz')].points").value(5))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ayşe Yılmaz')].me").value(true));

        mockMvc.perform(as(get("/api/gamification/leaderboard").param("facultyId", engineering.toString()).param("limit", "100"),
                        TestTokens.student(mehmet)))
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Mehmet Demir')]").doesNotExist())
                .andExpect(jsonPath("$.entries[?(@.displayName == 'Ayşe Yılmaz')]").exists())
                .andExpect(jsonPath("$.me.rank").doesNotExist())
                .andExpect(jsonPath("$.me.points").value(35));
    }

    @Test
    void studentsManageTheirOwnLeaderboardPreference() throws Exception {
        UUID student = UUID.randomUUID();
        mockMvc.perform(as(get("/api/gamification/users/me/leaderboard-preference"), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.displayMode").value("FULL_NAME"));
        mockMvc.perform(as(put("/api/gamification/users/me/leaderboard-preference"), TestTokens.student(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(put("/api/gamification/users/me/leaderboard-preference"), TestTokens.student(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":false,\"displayMode\":\"INITIALS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.displayMode").value("INITIALS"));
    }

    @Test
    void anUnreachableDirectoryOrABadLimitIsReportedClearly() throws Exception {
        mockMvc.perform(as(get("/api/gamification/leaderboard").param("limit", "101"), TestTokens.student(ayse)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_LIMIT"));
        doThrow(new RuntimeException("down")).when(directoryClient).profiles(any());
        mockMvc.perform(as(get("/api/gamification/leaderboard").param("period", "ALL_TIME"), TestTokens.student(ayse)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("LEADERBOARD_UNAVAILABLE"));
    }

    private void preference(UUID userId, boolean visible, String mode) throws Exception {
        mockMvc.perform(as(put("/api/gamification/users/me/leaderboard-preference"), TestTokens.student(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":" + visible + ",\"displayMode\":\"" + mode + "\"}"))
                .andExpect(status().isOk());
    }

    private static DirectoryProfile student(UUID id, String first, String last, UUID facultyId) {
        return new DirectoryProfile(id, first, last, List.of("STUDENT"), facultyId, "ACTIVE");
    }

    private void reputation(UUID userId, int points) {
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(points);
        reputationRepository.save(reputation);
    }

    private void history(UUID userId, int points, LocalDateTime at) {
        PointHistory row = new PointHistory();
        row.setUserId(userId);
        row.setActionType(ActionType.NOTE_SAVED);
        row.setReferenceId(UUID.randomUUID().toString());
        row.setPointsEarned(points);
        row.setCreatedAt(at.atZone(TurkishDates.ZONE).toInstant());
        historyRepository.save(row);
    }

    private void badge(UUID userId, BadgeType type) {
        UserBadge badge = new UserBadge();
        badge.setUserId(userId);
        badge.setBadgeType(type);
        badge.setEarnedAt(Instant.now());
        badgeRepository.save(badge);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
