package com.educonnect.assignmentservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class AssignmentServiceApplicationTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void startsOnAFreshDatabaseWithAllMigrationsApplied() throws Exception {
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().applied()).isNotEmpty();

		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
