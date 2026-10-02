package com.educonnect.apigateway;

import com.educonnect.common.test.TestTokens;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@GatewayIntegrationTest
class GatewayAuthorizationTest {

    private final UUID student = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private EchoBackend.Server echoServer;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearEcho() {
        echoServer.clear();
    }

    @AfterEach
    void resetClock() {
        GatewayTestJwtDecoder.resetClock();
    }

    @Test
    void publicEndpointsAreForwardedWithoutAToken() {
        List<String> publicReads = List.of("/api/clubs", "/api/clubs/" + UUID.randomUUID(), "/api/events",
                "/api/events/" + UUID.randomUUID(), "/api/auth/verify-email?token=abc", "/api/users/academic/catalog",
                "/api/users/academic/titles");
        for (String path : publicReads) {
            webTestClient.get().uri(path).exchange().expectStatus().isOk();
        }
        webTestClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isOk();
        webTestClient.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isOk();
        assertThat(echoServer.receivedPaths()).hasSize(publicReads.size() + 2);

        webTestClient.get().uri("/api/gamification/badges/first_step/image")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void everyOtherEndpointNeedsAToken() {
        String club = UUID.randomUUID().toString();
        List<Call> protectedCalls = List.of(
                new Call(HttpMethod.GET, "/api/clubs/my"),
                new Call(HttpMethod.GET, "/api/clubs/"),
                new Call(HttpMethod.POST, "/api/clubs"),
                new Call(HttpMethod.GET, "/api/clubs/" + club + "/members"),
                new Call(HttpMethod.DELETE, "/api/clubs/" + club),
                new Call(HttpMethod.PUT, "/api/events/" + club),
                new Call(HttpMethod.GET, "/api/posts"),
                new Call(HttpMethod.GET, "/api/users/me"),
                new Call(HttpMethod.GET, "/api/users/academic/catalog/all"),
                new Call(HttpMethod.POST, "/api/users/academic/faculties"),
                new Call(HttpMethod.POST, "/api/auth/request/club-official"),
                new Call(HttpMethod.POST, "/api/auth/verify-email"),
                new Call(HttpMethod.GET, "/api/auth/login"),
                new Call(HttpMethod.GET, "/api/gamification/leaderboard"),
                new Call(HttpMethod.GET, "/api/gamification/users/me/summary"),
                new Call(HttpMethod.PUT, "/api/gamification/badges/first_step/image"),
                new Call(HttpMethod.POST, "/api/ai/student-assistant"),
                new Call(HttpMethod.POST, "/api/llm/instructor-copilot"),
                new Call(HttpMethod.GET, "/api/courses"));
        for (Call call : protectedCalls) {
            webTestClient.method(call.method()).uri(call.path())
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED");
        }
        assertThat(echoServer.receivedPaths()).isEmpty();
    }

    @Test
    void validUserTokensAreForwardedToTheService() {
        webTestClient.get().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(student)))
                .exchange()
                .expectStatus().isOk();
        webTestClient.get().uri("/api/courses/my")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(student)))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE");
        webTestClient.get().uri("/api/gamification/leaderboard")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.academician(admin)))
                .exchange()
                .expectStatus().isEqualTo(503);
        assertThat(echoServer.receivedPaths()).containsExactly("/api/posts");
    }

    @Test
    void invalidExpiredForeignOrServiceTokensAreRejected() throws Exception {
        List<String> rejected = List.of("Bearer not-a-jwt", "Basic c3R1ZGVudDpwYXNz", "Bearer ",
                TestTokens.bearer(TestTokens.service("course-service")), TestTokens.bearer(foreignToken(student)));
        for (String authorization : rejected) {
            webTestClient.get().uri("/api/posts")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED");
            webTestClient.get().uri("/api/courses/my")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
        assertThat(echoServer.receivedPaths()).isEmpty();

        String token = TestTokens.bearer(TestTokens.student(student));
        GatewayTestJwtDecoder.shiftClock(Duration.ofHours(2));
        webTestClient.get().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isUnauthorized();
        GatewayTestJwtDecoder.resetClock();
        webTestClient.get().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void clientSuppliedIdentityHeadersAreReplacedByTheVerifiedOnes() {
        webTestClient.get().uri("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(student)))
                .header("X-Authenticated-User-Id", admin.toString())
                .header("x-authenticated-user-roles", "ROLE_ADMIN,ROLE_SERVICE")
                .header("X-Authenticated-User-Email", "admin@educonnect.local")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(EchoBackend.ECHO_USER_ID, student.toString())
                .expectHeader().valueEquals(EchoBackend.ECHO_USER_ROLES, "ROLE_STUDENT")
                .expectHeader().valueEquals(EchoBackend.ECHO_USER_EMAIL, student + "@test.educonnect.local");

        webTestClient.get().uri("/api/clubs")
                .header("X-Authenticated-User-Id", admin.toString())
                .header("X-Authenticated-User-Roles", "ROLE_ADMIN")
                .header("X-Authenticated-User-Email", "admin@educonnect.local")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(EchoBackend.ECHO_USER_ID)
                .expectHeader().doesNotExist(EchoBackend.ECHO_USER_ROLES)
                .expectHeader().doesNotExist(EchoBackend.ECHO_USER_EMAIL);
    }

    @Test
    void internalPathsAreNotReachableThroughTheGateway() {
        String id = UUID.randomUUID().toString();
        List<String> internalPaths = List.of(
                "/api/posts/internal/users/" + id + "/recent",
                "/api/posts/internal",
                "/api/courses/internal/instructors/" + id + "/course-ids",
                "/api/gamification/internal/users/" + id + "/summary",
                "/api/llm/internal/anything",
                "/api/users/%69nternal/users/" + id);
        List<String> tokens = List.of(TestTokens.bearer(TestTokens.service("user-service")),
                TestTokens.bearer(TestTokens.admin(admin)), TestTokens.bearer(TestTokens.student(student)));
        for (String path : internalPaths) {
            webTestClient.get().uri(raw(path))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.errorCode").isEqualTo("NOT_FOUND");
            for (String token : tokens) {
                webTestClient.get().uri(raw(path))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .exchange()
                        .expectStatus().isNotFound()
                        .expectBody().jsonPath("$.errorCode").isEqualTo("NOT_FOUND");
            }
        }
        webTestClient.put().uri("/api/posts/internal/{id}/moderation", id)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.service("llm-service")))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"decision\":\"TEMIZ\"}")
                .exchange()
                .expectStatus().isNotFound();
        assertThat(echoServer.receivedPaths()).isEmpty();
    }

    @Test
    void nonCanonicalInternalPathsAreNotForwardedEither() {
        String id = UUID.randomUUID().toString();
        List<String> variants = List.of(
                "/api/posts//internal/users/" + id + "/recent",
                "/api/posts/internal;v=1/users/" + id + "/recent",
                "/api/posts/x/../internal/users/" + id + "/recent",
                "/api/posts/%2e%2e/posts/internal/users/" + id + "/recent");
        for (String path : variants) {
            webTestClient.get().uri(raw(path))
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(student)))
                    .exchange()
                    .expectStatus().value(status -> assertThat(status).as(path).isIn(400, 404));
        }
        assertThat(echoServer.receivedPaths()).isEmpty();
    }

    private URI raw(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String foreignToken(UUID userId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId + "@test.educonnect.local")
                .claim("userId", userId.toString())
                .claim("roles", "ROLE_ADMIN")
                .audience(TestTokens.AUDIENCE)
                .issuer(TestTokens.ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    private record Call(HttpMethod method, String path) {
    }
}
