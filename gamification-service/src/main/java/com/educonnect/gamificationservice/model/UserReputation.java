package com.educonnect.gamificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_reputation", schema = "gamification_db")
public class UserReputation {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "highest_streak", nullable = false)
    private Integer highestStreak;

    @Column(name = "last_login_date")
    private LocalDate lastLoginDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UserReputation() {
    }

    public static UserReputation initialize(UUID userId) {
        UserReputation reputation = new UserReputation();
        reputation.setUserId(userId);
        reputation.setTotalPoints(0);
        reputation.setCurrentStreak(0);
        reputation.setHighestStreak(0);
        reputation.setVersion(0L);
        return reputation;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }

    public Integer getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(Integer currentStreak) {
        this.currentStreak = currentStreak;
    }

    public Integer getHighestStreak() {
        return highestStreak;
    }

    public void setHighestStreak(Integer highestStreak) {
        this.highestStreak = highestStreak;
    }

    public LocalDate getLastLoginDate() {
        return lastLoginDate;
    }

    public void setLastLoginDate(LocalDate lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

