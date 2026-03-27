package com.educonnect.userservice.dto.response;

import java.util.ArrayList;
import java.util.List;

public class GamificationSummaryDTO {
    private int totalPoints;
    private int currentStreak;
    private int highestStreak;
    private List<String> badges = new ArrayList<>();

    public GamificationSummaryDTO() {
    }

    public static GamificationSummaryDTO defaultValue() {
        GamificationSummaryDTO dto = new GamificationSummaryDTO();
        dto.setTotalPoints(0);
        dto.setCurrentStreak(0);
        dto.setHighestStreak(0);
        dto.setBadges(List.of());
        return dto;
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

