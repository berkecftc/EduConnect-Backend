package com.educonnect.authservices.service;

import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTServiceServiceTokenTest {

    private static KeyPair keyPair;
    private static JWTService jwtService;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        jwtService = new JWTService(pem, "educonnect-auth", "educonnect", "educonnect-internal",
                Duration.ofMinutes(10), 900000, 604800000);
    }

    @Test
    void serviceTokenCarriesInternalAudienceAndServiceClaims() {
        String token = jwtService.generateServiceToken("notification-service");

        Claims claims = Jwts.parser().verifyWith((RSAPublicKey) keyPair.getPublic()).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("notification-service");
        assertThat(claims.getAudience()).containsExactly("educonnect-internal");
        assertThat(claims.get("token_use", String.class)).isEqualTo("service");
        assertThat(claims.get("roles", String.class)).isEqualTo(JWTService.SERVICE_ROLE);
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(Duration.ofMinutes(10).toMillis());
        assertThat(jwtService.extractServiceClientId(token)).contains("notification-service");
    }

    @Test
    void serviceTokenIsNotAcceptedAsUserToken() {
        String token = jwtService.generateServiceToken("notification-service");

        assertThatThrownBy(() -> jwtService.extractUsername(token)).isInstanceOf(Exception.class);
    }

    @Test
    void userTokenIsNotAcceptedAsServiceToken() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("student@educonnect.com");
        user.setRoles(Set.of(Role.ROLE_STUDENT));

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractServiceClientId(token)).isEmpty();
        assertThat(jwtService.extractUsername(token)).isEqualTo("student@educonnect.com");
    }

    @Test
    void garbageIsNotAServiceToken() {
        assertThat(jwtService.extractServiceClientId("not-a-jwt")).isEmpty();
    }
}
