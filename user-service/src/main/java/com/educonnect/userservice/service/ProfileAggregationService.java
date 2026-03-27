package com.educonnect.userservice.service;

import com.educonnect.userservice.client.dto.GamificationSummaryClientResponse;
import com.educonnect.userservice.client.dto.RecentPostClientResponse;
import com.educonnect.userservice.dto.response.GamificationSummaryDTO;
import com.educonnect.userservice.dto.response.RecentPostDTO;
import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.dto.response.UserProfileResponseDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class ProfileAggregationService {

    private static final Duration EXTERNAL_CALL_TIMEOUT = Duration.ofMillis(500);
    private static final Duration AGGREGATION_TIMEOUT = Duration.ofMillis(900);

    private final ProfileService profileService;
    private final ProfileRemoteService profileRemoteService;
    private final Executor profileAggregationExecutor;

    public ProfileAggregationService(ProfileService profileService,
                                     ProfileRemoteService profileRemoteService,
                                     @Qualifier("profileAggregationExecutor") Executor profileAggregationExecutor) {
        this.profileService = profileService;
        this.profileRemoteService = profileRemoteService;
        this.profileAggregationExecutor = profileAggregationExecutor;
    }

    public UserProfileResponseDTO getAggregatedUserProfile(UUID userId) {
        UserProfileResponse baseProfile = profileService.getUserProfile(userId);

        CompletableFuture<GamificationSummaryClientResponse> gamificationFuture = CompletableFuture
                .supplyAsync(() -> profileRemoteService.getGamificationSummary(userId), profileAggregationExecutor)
                .completeOnTimeout(defaultGamification(), EXTERNAL_CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> defaultGamification());

        CompletableFuture<List<RecentPostClientResponse>> recentPostsFuture = CompletableFuture
                .supplyAsync(() -> profileRemoteService.getRecentPosts(userId), profileAggregationExecutor)
                .completeOnTimeout(List.of(), EXTERNAL_CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> List.of());

        CompletableFuture.allOf(gamificationFuture, recentPostsFuture)
                .orTimeout(AGGREGATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> null)
                .join();

        return merge(baseProfile, gamificationFuture.join(), recentPostsFuture.join());
    }

    private UserProfileResponseDTO merge(UserProfileResponse baseProfile,
                                         GamificationSummaryClientResponse gamification,
                                         List<RecentPostClientResponse> posts) {
        UserProfileResponseDTO response = new UserProfileResponseDTO();
        response.setId(baseProfile.getId());
        response.setFirstName(baseProfile.getFirstName());
        response.setLastName(baseProfile.getLastName());
        response.setEmail(baseProfile.getEmail());
        response.setProfileImageUrl(baseProfile.getProfileImageUrl());
        response.setRole(baseProfile.getRole());
        response.setStudentNumber(baseProfile.getStudentNumber());
        response.setTitle(baseProfile.getTitle());
        response.setDepartment(baseProfile.getDepartment());
        response.setBio(baseProfile.getBio());

        GamificationSummaryDTO gamificationSummary = new GamificationSummaryDTO();
        gamificationSummary.setTotalPoints(gamification.getTotalPoints());
        gamificationSummary.setCurrentStreak(gamification.getCurrentStreak());
        gamificationSummary.setHighestStreak(gamification.getHighestStreak());
        gamificationSummary.setBadges(gamification.getBadges());

        response.setGamification(gamificationSummary);
        response.setRecentPosts(posts.stream().map(this::toRecentPostDTO).toList());
        response.setProfileCompletionPercentage(calculateProfileCompletionPercentage(baseProfile));
        return response;
    }

    private RecentPostDTO toRecentPostDTO(RecentPostClientResponse post) {
        RecentPostDTO dto = new RecentPostDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setCreatedAt(post.getCreatedAt());
        return dto;
    }

    private GamificationSummaryClientResponse defaultGamification() {
        GamificationSummaryClientResponse fallback = new GamificationSummaryClientResponse();
        fallback.setTotalPoints(0);
        fallback.setCurrentStreak(0);
        fallback.setHighestStreak(0);
        fallback.setBadges(List.of());
        return fallback;
    }

    private int calculateProfileCompletionPercentage(UserProfileResponse profile) {
        int totalFields = 6;
        int completedFields = 0;

        if (hasText(profile.getFirstName())) {
            completedFields++;
        }
        if (hasText(profile.getLastName())) {
            completedFields++;
        }
        if (hasText(profile.getProfileImageUrl())) {
            completedFields++;
        }
        if (hasText(profile.getDepartment())) {
            completedFields++;
        }
        if (hasText(profile.getBio())) {
            completedFields++;
        }
        if (hasText(profile.getEmail())) {
            completedFields++;
        }

        if ("Student".equalsIgnoreCase(profile.getRole())) {
            totalFields++;
            if (hasText(profile.getStudentNumber())) {
                completedFields++;
            }
        } else if ("Academician".equalsIgnoreCase(profile.getRole())) {
            totalFields++;
            if (hasText(profile.getTitle())) {
                completedFields++;
            }
        }

        return (int) Math.round((completedFields * 100.0) / totalFields);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

