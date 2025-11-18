package com.educonnect.apigateway.util;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;


@Component
public class JwtUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtUtil.class);

    // api-gateway.yml dosyasından (Config Server üzerinden) okunacak
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Token'ı doğrular. İmza veya süre geçerliliğini yitirmişse hata fırlatır.
     */
    public void validateToken(final String token) {
        Jwts.parser().verifyWith(getSignInKey()).build().parseSignedClaims(token);
    }

    /**
     * Token içerisinden kullanıcı adını çıkarır.
     */
    public String extractUsername(String token) {
        return Jwts.parser().verifyWith(getSignInKey()).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    /**
     * Token içerisinden userId claim'ini çıkarır.
     */
    public String extractUserId(String token) {
        return Jwts.parser().verifyWith(getSignInKey()).build()
                .parseSignedClaims(token).getPayload().get("userId", String.class);
    }

    /**
     * Token içerisinden roles claim'ini çıkarır (comma-separated).
     */
    public String extractRoles(String token) {
        return Jwts.parser().verifyWith(getSignInKey()).build()
                .parseSignedClaims(token).getPayload().get("roles", String.class);
    }

    private SecretKey getSignInKey() {
        SecretKey key = deriveHmacKey(secret);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("JWT key fingerprint (SHA-256 Base64): {}", fingerprint(key));
        }
        return key;
    }

    // Base64/RAW prefix algılama (case-insensitive); tanınmazsa otomatik algı.
    private static SecretKey deriveHmacKey(String secretValue) {
        if (secretValue == null || secretValue.isBlank()) {
            throw new IllegalStateException("jwt.secret property is missing or blank");
        }
        String value = secretValue.trim();
        byte[] keyBytes;

        int colon = value.indexOf(':');
        if (colon > 0 && colon < 16) {
            String prefix = value.substring(0, colon).trim().toUpperCase();
            String rest = value.substring(colon + 1).trim();
            if ("BASE64".equals(prefix)) {
                keyBytes = Decoders.BASE64.decode(rest);
                return Keys.hmacShaKeyFor(keyBytes);
            } else if ("RAW".equals(prefix)) {
                keyBytes = rest.getBytes(StandardCharsets.UTF_8);
                return Keys.hmacShaKeyFor(keyBytes);
            }
            value = rest.isEmpty() ? value : rest;
        }

        try {
            keyBytes = Decoders.BASE64.decode(value);
        } catch (IllegalArgumentException ex) {
            keyBytes = value.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static String fingerprint(SecretKey key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getEncoded());
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            return "n/a";
        }
    }
}