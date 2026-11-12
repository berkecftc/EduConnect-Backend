package com.educonnect.notificationservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.notificationservice.config.NotificationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Component
public class UnsubscribeTokens {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;

    public UnsubscribeTokens(NotificationProperties properties) {
        this.secret = properties.unsubscribeSecret().getBytes(StandardCharsets.UTF_8);
    }

    public record Subscription(UUID userId, NotificationCategory category) {
    }

    public String issue(UUID userId, NotificationCategory category) {
        String payload = userId + ":" + category.name();
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + ENCODER.encodeToString(sign(payload));
    }

    public Optional<Subscription> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        try {
            String payload = new String(DECODER.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] signature = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(payload), signature)) {
                return Optional.empty();
            }
            int colon = payload.indexOf(':');
            if (colon <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Subscription(UUID.fromString(payload.substring(0, colon)),
                    NotificationCategory.valueOf(payload.substring(colon + 1))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unsubscribe token could not be signed", e);
        }
    }
}
