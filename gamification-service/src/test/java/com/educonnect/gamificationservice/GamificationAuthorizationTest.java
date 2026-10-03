package com.educonnect.gamificationservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@GamificationIntegrationTest
class GamificationAuthorizationTest {

    private final UUID student = UUID.randomUUID();
    private final UUID otherStudent = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserReputationRepository userReputationRepository;

    @BeforeEach
    void seed() {
        saveReputation(student, 420);
        saveReputation(otherStudent, 7);
    }

    @Test
    void summaryOfMeAlwaysBelongsToTheTokenOwner() throws Exception {
        mockMvc.perform(as(get("/api/gamification/users/me/summary"), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(420));
        mockMvc.perform(as(get("/api/gamification/users/me/summary"), TestTokens.student(otherStudent))
                        .header("X-Authenticated-User-Id", student.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(7));
    }

    @Test
    void summaryOfMeNeedsAVerifiedUserToken() throws Exception {
        mockMvc.perform(get("/api/gamification/users/me/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gamification/users/me/summary").header("X-Authenticated-User-Id", student.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/gamification/users/me/summary"), TestTokens.service("user-service")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gamification/users/me/summary").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalSummaryAcceptsOnlyServiceTokens() throws Exception {
        String path = "/api/gamification/internal/users/{id}/summary";
        mockMvc.perform(get(path, student))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(as(get(path, student), TestTokens.student(otherStudent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(get(path, student), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get(path, student), TestTokens.user(otherStudent, "ROLE_STUDENT,ROLE_SERVICE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get(path, student), TestTokens.service("user-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(420));
    }

    @Test
    void leaderboardIsForStudentsOnly() throws Exception {
        mockMvc.perform(get("/api/gamification/leaderboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/gamification/leaderboard"), TestTokens.academician(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("LEADERBOARD_STUDENTS_ONLY"));
        mockMvc.perform(as(get("/api/gamification/leaderboard"), TestTokens.user(UUID.randomUUID(), "ROLE_STAFF,PERM_MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void badgeImagesArePublic() throws Exception {
        mockMvc.perform(get("/api/gamification/badges/{type}/image", "STREAK_LEGEND"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
        mockMvc.perform(get("/api/gamification/badges/{type}/image", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void badgeImagesResolveLowercaseNamesUnderATurkishLocale() throws Exception {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            mockMvc.perform(get("/api/gamification/badges/{type}/image", "first_step"))
                    .andExpect(status().isOk());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private void saveReputation(UUID userId, int points) {
        UserReputation reputation = UserReputation.initialize(userId);
        reputation.setTotalPoints(points);
        userReputationRepository.save(reputation);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
