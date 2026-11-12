package com.educonnect.userservice.dto.response;

import java.util.ArrayList;
import java.util.List;

public class GamificationSummaryDTO {
    private Integer totalPoints;
    private boolean leaderboardVisible;
    private Integer currentStreak;
    private Integer highestStreak;
    private List<BadgeInfoDTO> badges = new ArrayList<>();

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

    public Integer getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }

    public boolean isLeaderboardVisible() {
        return leaderboardVisible;
    }

    public void setLeaderboardVisible(boolean leaderboardVisible) {
        this.leaderboardVisible = leaderboardVisible;
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

    public List<BadgeInfoDTO> getBadges() {
        return badges;
    }

    public void setBadges(List<BadgeInfoDTO> badges) {
        this.badges = badges == null ? List.of() : badges;
    }
}
