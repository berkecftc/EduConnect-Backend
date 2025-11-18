package com.educonnect.authservices.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JWTService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JWTService.class);

    @Value("${jwt.secret}")
    private String JWT_SECRET;

    @Value("${jwt.expiration-ms}")
    private long JWT_EXPIRATION;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // UserDetails aslında User objesi, ID ve Role'leri ekleyelim
        if (userDetails instanceof com.educonnect.authservices.models.User user) {
            claims.put("userId", user.getId().toString());
            // Birden fazla role olabilir, comma-separated string olarak ekle
            String rolesString = user.getRoles().stream()
                    .map(Enum::name)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            claims.put("roles", rolesString); // Örn: "ADMIN,STUDENT"
        }
        return generateToken(claims, userDetails);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            String username = claims.getSubject();
            return username != null && username.equals(userDetails.getUsername())
                    && claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return parseClaims(token);
    }

    private Claims parseClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey getSignInKey() {
        SecretKey key = deriveHmacKey(JWT_SECRET);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("JWT key fingerprint (SHA-256 Base64): {}", fingerprint(key));
        }
        return key;
    }

    // Prefix algılama (case-insensitive): "BASE64:<val>" veya "RAW:<val>"; tanınmazsa otomatik algı.
    private static SecretKey deriveHmacKey(String secretValue) {
        if (secretValue == null || secretValue.isBlank()) {
            throw new IllegalStateException("jwt.secret property is missing or blank");
        }
        String value = secretValue.trim();
        byte[] keyBytes;

        int colon = value.indexOf(':');
        if (colon > 0 && colon < 16) { // "BASE64", "RAW" gibi kısa prefixler için yeterli
            String prefix = value.substring(0, colon).trim().toUpperCase();
            String rest = value.substring(colon + 1).trim();
            if ("BASE64".equals(prefix)) {
                keyBytes = Decoders.BASE64.decode(rest);
                return Keys.hmacShaKeyFor(keyBytes);
            } else if ("RAW".equals(prefix)) {
                keyBytes = rest.getBytes(StandardCharsets.UTF_8);
                return Keys.hmacShaKeyFor(keyBytes);
            }
            // tanınmayan prefix: otomatik algıya düş
            value = rest.isEmpty() ? value : rest;
        }

        // Otomatik algı: Base64 dene, olmazsa RAW olarak kabul et
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