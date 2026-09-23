package com.educonnect.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VerifiedIdentityFilterTest {

    private static KeyPair keyPair;
    private static VerifiedIdentityFilter filter;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        filter = new VerifiedIdentityFilter(decoder, ServiceIdentity.DEFAULT_INTERNAL_AUDIENCE);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userTokenPopulatesIdentityHeadersAndDropsServiceRole() throws Exception {
        String token = sign(Map.of(
                "sub", "student@educonnect.com",
                "aud", List.of("educonnect"),
                "userId", "11111111-1111-1111-1111-111111111111",
                "roles", "ROLE_STUDENT,ROLE_SERVICE"));

        Result result = run("/api/posts/feed", token, Map.of());

        assertThat(result.request().getHeader(IdentityHeaders.USER_ID)).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(result.request().getHeader(IdentityHeaders.USER_EMAIL)).isEqualTo("student@educonnect.com");
        assertThat(authorities(result.authentication())).containsExactly("ROLE_STUDENT");
    }

    @Test
    void serviceTokenOnInternalPathAuthenticatesAsService() throws Exception {
        String token = sign(Map.of(
                "sub", "notification-service",
                "aud", List.of(ServiceIdentity.DEFAULT_INTERNAL_AUDIENCE),
                ServiceIdentity.TOKEN_USE_CLAIM, ServiceIdentity.TOKEN_USE_SERVICE,
                "roles", ServiceIdentity.AUTHORITY));

        Result result = run("/api/clubs/internal/abc/members/ids", token,
                Map.of(IdentityHeaders.USER_ID, "22222222-2222-2222-2222-222222222222"));

        assertThat(result.authentication().getName()).isEqualTo("notification-service");
        assertThat(authorities(result.authentication())).containsExactly(ServiceIdentity.AUTHORITY);
        assertThat(result.request().getHeader(IdentityHeaders.USER_ID)).isNull();
    }

    @Test
    void serviceTokenOnPublicPathIsIgnored() throws Exception {
        String token = sign(Map.of(
                "sub", "notification-service",
                "aud", List.of(ServiceIdentity.DEFAULT_INTERNAL_AUDIENCE),
                ServiceIdentity.TOKEN_USE_CLAIM, ServiceIdentity.TOKEN_USE_SERVICE));

        Result result = run("/api/clubs/my-memberships", token, Map.of());

        assertThat(result.authentication()).isNull();
        assertThat(result.request().getHeader(IdentityHeaders.USER_ID)).isNull();
    }

    @Test
    void internalAudienceWithoutServiceClaimIsNotAService() throws Exception {
        String token = sign(Map.of(
                "sub", "someone@educonnect.com",
                "aud", List.of(ServiceIdentity.DEFAULT_INTERNAL_AUDIENCE),
                "roles", ServiceIdentity.AUTHORITY));

        Result result = run("/api/clubs/internal/abc/members/ids", token, Map.of());

        assertThat(authorities(result.authentication())).doesNotContain(ServiceIdentity.AUTHORITY);
    }

    @Test
    void forgedIdentityHeadersWithoutTokenAreRemoved() throws Exception {
        Result result = run("/api/posts/feed", null, Map.of(
                IdentityHeaders.USER_ID, "33333333-3333-3333-3333-333333333333",
                IdentityHeaders.USER_ROLES, "ROLE_ADMIN"));

        assertThat(result.authentication()).isNull();
        assertThat(result.request().getHeader(IdentityHeaders.USER_ID)).isNull();
        assertThat(result.request().getHeader(IdentityHeaders.USER_ROLES)).isNull();
    }

    private static Result run(String path, String token, Map<String, String> headers) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        headers.forEach(request::addHeader);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set((HttpServletRequest) req);
                authentication.set(SecurityContextHolder.getContext().getAuthentication());
            }
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return new Result(seen.get(), authentication.get());
    }

    private static List<String> authorities(Authentication authentication) {
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static String sign(Map<String, Object> claims) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        claims.forEach(builder::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    private record Result(HttpServletRequest request, Authentication authentication) {
    }
}
