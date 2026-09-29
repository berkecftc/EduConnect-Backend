package com.educonnect.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@GatewayIntegrationTest
class ApiGatewayApplicationTests {

	@Autowired
	private RouteLocator routeLocator;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void startsWithAllRoutesAndRejectsAnonymousCalls() {
		assertThat(routeLocator.getRoutes().map(Route::getId).collectList().block())
				.contains("course-service", "llm-routes", "gamification-routes", "api-docs-auth-services");

		webTestClient.get().uri("/api/courses/my")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED");
	}
}
