package com.educonnect.userservice.service;

import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.dto.response.UserProfileResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAggregationServiceTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private ProfileRemoteService profileRemoteService;

    private final Executor directExecutor = Runnable::run;

    @Test
    void shouldAggregateAndCalculateCompletionAsHundredForCompleteStudentProfile() {
        UUID userId = UUID.randomUUID();
        UserProfileResponse baseProfile = new UserProfileResponse();
        baseProfile.setId(userId);
        baseProfile.setFirstName("Ada");
        baseProfile.setLastName("Lovelace");
        baseProfile.setEmail("ada@educonnect.com");
        baseProfile.setProfileImageUrl("avatar.png");
        baseProfile.setRole("Student");
        baseProfile.setStudentNumber("S12345");
        baseProfile.setDepartment("Computer Science");
        baseProfile.setBio("Mathematics enthusiast");

        when(profileService.getUserProfile(userId)).thenReturn(baseProfile);
        when(profileRemoteService.getGamificationSummary(userId)).thenThrow(new RuntimeException("down"));
        when(profileRemoteService.getRecentPosts(userId)).thenThrow(new RuntimeException("down"));

        ProfileAggregationService service = new ProfileAggregationService(
                profileService,
                profileRemoteService,
                directExecutor
        );

        UserProfileResponseDTO result = service.getAggregatedUserProfile(userId);

        assertNotNull(result);
        assertEquals(100, result.getProfileCompletionPercentage());
        assertNotNull(result.getGamification());
        assertEquals(0, result.getGamification().getTotalPoints());
        assertEquals(0, result.getGamification().getCurrentStreak());
        assertTrue(result.getRecentPosts().isEmpty());
    }
}


