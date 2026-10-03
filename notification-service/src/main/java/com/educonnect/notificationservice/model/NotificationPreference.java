package com.educonnect.notificationservice.model;

import com.educonnect.common.messaging.notification.NotificationCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreference.Key.class)
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(UUID userId, NotificationCategory category, boolean emailEnabled) {
        this.userId = userId;
        this.category = category;
        update(emailEnabled);
    }

    public final void update(boolean enabled) {
        this.emailEnabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public NotificationCategory getCategory() { return category; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private UUID userId;
        private NotificationCategory category;

        public Key() {
        }

        public Key(UUID userId, NotificationCategory category) {
            this.userId = userId;
            this.category = category;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(userId, key.userId) && category == key.category;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, category);
        }
    }
}
