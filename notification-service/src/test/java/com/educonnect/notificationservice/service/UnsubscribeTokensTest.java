package com.educonnect.notificationservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.notificationservice.config.NotificationProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnsubscribeTokensTest {

    private final UnsubscribeTokens tokens = new UnsubscribeTokens(new NotificationProperties("first-secret", null, null, null));

    @Test
    void issuedTokensVerifyBackToTheSameSubscription() {
        UUID userId = UUID.randomUUID();

        String token = tokens.issue(userId, NotificationCategory.CLUB_NEWS);

        assertThat(tokens.verify(token)).contains(new UnsubscribeTokens.Subscription(userId, NotificationCategory.CLUB_NEWS));
    }

    @Test
    void tamperedForeignOrMalformedTokensAreRejected() {
        UUID userId = UUID.randomUUID();
        String token = tokens.issue(userId, NotificationCategory.COMMUNITY);
        String signature = token.substring(token.indexOf('.'));
        String otherPayload = tokens.issue(UUID.randomUUID(), NotificationCategory.COMMUNITY);
        UnsubscribeTokens other = new UnsubscribeTokens(new NotificationProperties("second-secret", null, null, null));

        assertThat(tokens.verify(otherPayload.substring(0, otherPayload.indexOf('.')) + signature)).isEmpty();
        assertThat(other.verify(token)).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();
        assertThat(tokens.verify("no-dot")).isEmpty();
        assertThat(tokens.verify("." + signature)).isEmpty();
        assertThat(tokens.verify("%%%." + signature)).isEmpty();
    }
}
