package com.educonnect.userservice.client.dto;

import java.util.ArrayList;
import java.util.List;

public class GamificationSummaryClientResponse {
    private int totalPoints;
    private int currentStreak;
    private int highestStreak;
    private List<String> badges = new ArrayList<>();

    public GamificationSummaryClientResponse() {
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getHighestStreak() {
        return highestStreak;
    }

    public void setHighestStreak(int highestStreak) {
        this.highestStreak = highestStreak;
    }

    public List<String> getBadges() {
        return badges;
    }

    public void setBadges(List<String> badges) {
        this.badges = badges == null ? List.of() : badges;
    }
}

