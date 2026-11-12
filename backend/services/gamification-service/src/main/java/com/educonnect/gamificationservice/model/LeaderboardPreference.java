package com.educonnect.gamificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leaderboard_preferences")
public class LeaderboardPreference {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean visible = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode", nullable = false, length = 20)
    private DisplayMode displayMode = DisplayMode.FULL_NAME;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaderboardPreference() {
    }

    public LeaderboardPreference(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public static LeaderboardPreference defaults(UUID userId) {
        return new LeaderboardPreference(userId);
    }

    public void update(boolean visible, DisplayMode displayMode) {
        this.visible = visible;
        this.displayMode = displayMode;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public boolean isVisible() { return visible; }
    public DisplayMode getDisplayMode() { return displayMode; }
    public Instant getUpdatedAt() { return updatedAt; }
}
