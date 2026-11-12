package com.educonnect.eurekaserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EurekaServerApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void startsAndServesTheRegistryOnlyToAuthenticatedClients() {
		ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
		assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(health.getBody()).contains("\"status\":\"UP\"");

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		HttpEntity<Void> request = new HttpEntity<>(headers);

		ResponseEntity<String> anonymous = restTemplate.exchange("/eureka/apps", HttpMethod.GET, request, String.class);
		assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> registry = restTemplate.withBasicAuth("integration", "integration-secret")
				.exchange("/eureka/apps", HttpMethod.GET, request, String.class);
		assertThat(registry.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(registry.getBody()).contains("applications");
	}
}
