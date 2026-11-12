package com.educonnect.gamificationservice;

import com.educonnect.gamificationservice.dto.event.GamificationEvent;
import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.UserReputation;
import com.educonnect.gamificationservice.repository.UserReputationRepository;
import com.educonnect.gamificationservice.service.GamificationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@GamificationIntegrationTest
class GamificationServiceApplicationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GamificationService gamificationService;

    @Autowired
    private UserReputationRepository userReputationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theFirstEventOfANewUserCreatesTheirReputation() {
        UUID newUser = UUID.randomUUID();

        gamificationService.processEvent(new GamificationEvent(newUser, ActionType.ANSWER_ACCEPTED,
                "COMMENT:" + UUID.randomUUID(), OffsetDateTime.now()));

        UserReputation reputation = userReputationRepository.findById(newUser).orElseThrow();
        assertThat(reputation.getTotalPoints()).isEqualTo(15);
        assertThat(reputation.getCurrentStreak()).isEqualTo(1);
        assertThat(reputation.getVersion()).isZero();
        assertThat(jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from outbox_messages "
                + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ?", String.class, "%" + newUser + "%"))
                .isNotEmpty()
                .allSatisfy(body -> assertThat(body).contains("\"category\":\"ACHIEVEMENT\"", "\"type\":\"BADGE_EARNED\"", "Yeni rozet: "))
                .anySatisfy(body -> assertThat(body).contains("\"dedupKey\":\"badge:FIRST_STEP\""));
    }

    @Test
    void startsOnAFreshDatabaseWithAllMigrationsApplied() throws Exception {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).isNotEmpty();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
