package com.educonnect.userservice.service;

import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.dto.response.UserProfileResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileViewServiceTest {

    private final UUID ownerId = UUID.randomUUID();

    @Mock
    private ProfileService profileService;

    @Mock
    private ProfileAggregationService profileAggregationService;

    @InjectMocks
    private ProfileViewService profileViewService;

    private UserProfileResponse cachedProfile;

    @BeforeEach
    void setUp() {
        cachedProfile = new UserProfileResponse();
        cachedProfile.setId(ownerId);
        cachedProfile.setFirstName("Ayşe");
        cachedProfile.setEmail("ayse@example.edu");
        cachedProfile.setStudentNumber("2020001");
    }

    @Test
    void getProfile_whenViewerIsOwner_shouldIncludeEmail() {
        when(profileService.getUserProfile(ownerId)).thenReturn(cachedProfile);

        UserProfileResponse result = profileViewService.getProfile(ownerId, ownerId, "ROLE_STUDENT");

        assertThat(result.getEmail()).isEqualTo("ayse@example.edu");
    }

    @Test
    void getProfile_whenViewerIsAdmin_shouldIncludeEmail() {
        when(profileService.getUserProfile(ownerId)).thenReturn(cachedProfile);

        UserProfileResponse result = profileViewService.getProfile(ownerId, UUID.randomUUID(), "ROLE_ADMIN");

        assertThat(result.getEmail()).isEqualTo("ayse@example.edu");
    }

    @Test
    void getProfile_whenViewerIsSomeoneElse_shouldHideEmailWithoutTouchingCachedProfile() {
        when(profileService.getUserProfile(ownerId)).thenReturn(cachedProfile);

        UserProfileResponse result = profileViewService.getProfile(ownerId, UUID.randomUUID(), "ROLE_STUDENT,ROLE_CLUB_OFFICIAL");

        assertThat(result.getEmail()).isNull();
        assertThat(result.getFirstName()).isEqualTo("Ayşe");
        assertThat(result.getStudentNumber()).isEqualTo("2020001");
        assertThat(cachedProfile.getEmail()).isEqualTo("ayse@example.edu");
    }

    @Test
    void getProfile_whenAnonymous_shouldHideEmail() {
        when(profileService.getUserProfile(ownerId)).thenReturn(cachedProfile);

        UserProfileResponse result = profileViewService.getProfile(ownerId, null, null);

        assertThat(result.getEmail()).isNull();
    }

    @Test
    void getAggregatedProfile_whenViewerIsSomeoneElse_shouldHideEmail() {
        UserProfileResponseDTO aggregated = new UserProfileResponseDTO();
        aggregated.setEmail("ayse@example.edu");
        when(profileService.getUserProfile(ownerId)).thenReturn(cachedProfile);
        when(profileAggregationService.getAggregatedUserProfile(ownerId)).thenReturn(aggregated);

        UserProfileResponseDTO result = profileViewService.getAggregatedProfile(ownerId, UUID.randomUUID(), "ROLE_ACADEMICIAN");

        assertThat(result.getEmail()).isNull();
    }

    @Test
    void getProfile_whenProfileMissing_shouldReturnNotFound() {
        when(profileService.getUserProfile(ownerId)).thenThrow(new RuntimeException("Profile not found"));

        assertThatThrownBy(() -> profileViewService.getProfile(ownerId, ownerId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getProfileForService_shouldReturnFullProfile() {
        when(profileService.getUserProfile(ownerId)).thenReturn(cachedProfile);

        assertThat(profileViewService.getProfileForService(ownerId).getEmail()).isEqualTo("ayse@example.edu");
    }
}
