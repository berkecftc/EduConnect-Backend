package com.educonnect.common.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class TestTokens {

    public static final String ISSUER = "educonnect-auth";
    public static final String AUDIENCE = "educonnect";
    public static final String INTERNAL_AUDIENCE = "educonnect-internal";

    private static final KeyPair KEY_PAIR = generate();

    private TestTokens() {
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    public static String student(UUID userId) {
        return user(userId, "ROLE_STUDENT");
    }

    public static String academician(UUID userId) {
        return user(userId, "ROLE_ACADEMICIAN");
    }

    public static String admin(UUID userId) {
        return user(userId, "ROLE_ADMIN");
    }

    public static String user(UUID userId, String roles) {
        return sign(new JWTClaimsSet.Builder()
                .subject(userId + "@test.educonnect.local")
                .claim("userId", userId.toString())
                .claim("roles", roles)
                .audience(AUDIENCE));
    }

    public static String service(String clientId) {
        return sign(new JWTClaimsSet.Builder()
                .subject(clientId)
                .claim("token_use", "service")
                .claim("roles", "ROLE_SERVICE")
                .audience(INTERNAL_AUDIENCE));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sign(JWTClaimsSet.Builder claims) {
        Instant now = Instant.now();
        JWTClaimsSet set = claims
                .issuer(ISSUER)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), set);
        try {
            jwt.sign(new RSASSASigner(KEY_PAIR.getPrivate()));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Test token could not be signed", ex);
        }
        return jwt.serialize();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
