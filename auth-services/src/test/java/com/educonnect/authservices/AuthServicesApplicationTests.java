package com.educonnect.authservices;

import com.educonnect.common.test.MinioTestContainer;
import com.educonnect.common.test.PostgresTestContainer;
import com.educonnect.common.test.RabbitTestContainer;
import com.educonnect.common.test.RedisTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, RabbitTestContainer.class, RedisTestContainer.class, MinioTestContainer.class})
class AuthServicesApplicationTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void signingKey(DynamicPropertyRegistry registry) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		String label = "PRIVATE" + " KEY";
		String pem = "-----BEGIN " + label + "-----\n"
				+ Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
				+ "\n-----END " + label + "-----";
		registry.add("jwt.private-key", () -> pem);
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
