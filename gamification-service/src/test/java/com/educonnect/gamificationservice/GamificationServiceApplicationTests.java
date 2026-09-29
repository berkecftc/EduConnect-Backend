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

    @Test
    void theFirstEventOfANewUserCreatesTheirReputation() {
        UUID newUser = UUID.randomUUID();

        gamificationService.processEvent(new GamificationEvent(newUser, ActionType.POST_PUBLISHED,
                "POST:" + UUID.randomUUID(), OffsetDateTime.now()));

        UserReputation reputation = userReputationRepository.findById(newUser).orElseThrow();
        assertThat(reputation.getTotalPoints()).isPositive();
        assertThat(reputation.getVersion()).isZero();
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
