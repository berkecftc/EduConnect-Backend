package com.educonnect.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigServerApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void startsAndServesConfigurationOnlyToAuthenticatedClients() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));

		mockMvc.perform(get("/integration-app/default"))
				.andExpect(status().isUnauthorized());

		String credentials = Base64.getEncoder()
				.encodeToString("integration:integration-secret".getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(get("/integration-app/default").header(HttpHeaders.AUTHORIZATION, "Basic " + credentials))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("integration-app"))
				.andExpect(jsonPath("$.propertySources[0].source['educonnect.integration.greeting']")
						.value("served-by-config-server"));
	}
}
