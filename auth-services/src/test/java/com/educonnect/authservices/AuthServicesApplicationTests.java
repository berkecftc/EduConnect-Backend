package com.educonnect.authservices;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
class AuthServicesApplicationTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@Test
	void unqualifiedRestClientBuilderLookupsDoNotGetTheLoadBalancedBuilder() {
		RestClient.Builder loadBalanced = context.getBean("internalRestClientBuilder", RestClient.Builder.class);

		assertThat(context.getBeanProvider(RestClient.Builder.class).getIfAvailable(RestClient::builder))
				.isNotSameAs(loadBalanced);
	}

	@Test
	void startsOnAFreshDatabaseWithAllMigrationsApplied() throws Exception {
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().applied()).isNotEmpty();

		mockMvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"));
	}
}
