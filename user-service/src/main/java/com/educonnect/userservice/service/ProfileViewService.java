package com.educonnect.userservice.service;

import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.dto.response.UserProfileResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileViewService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final int MAX_BATCH_SIZE = 500;

    private final ProfileService profileService;
    private final ProfileAggregationService profileAggregationService;

    public ProfileViewService(ProfileService profileService, ProfileAggregationService profileAggregationService) {
        this.profileService = profileService;
        this.profileAggregationService = profileAggregationService;
    }

    public UserProfileResponse getProfile(UUID userId, UUID viewerId, String viewerRoles) {
        UserProfileResponse profile = findProfile(userId);
        return canSeeEmail(userId, viewerId, viewerRoles) ? profile : profile.withoutEmail();
    }

    public UserProfileResponseDTO getAggregatedProfile(UUID userId, UUID viewerId, String viewerRoles) {
        findProfile(userId);
        UserProfileResponseDTO profile = profileAggregationService.getAggregatedUserProfile(userId);
        if (!canSeeEmail(userId, viewerId, viewerRoles)) {
            profile.setEmail(null);
        }
        return profile;
    }

    public UserProfileResponse getProfileForService(UUID userId) {
        return findProfile(userId);
    }

    public List<UserProfileResponse> getProfilesForService(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (userIds.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En fazla " + MAX_BATCH_SIZE + " profil istenebilir.");
        }
        return profileService.getUserProfiles(new LinkedHashSet<>(userIds));
    }

    public UserProfileResponse getStudentByStudentNumberForService(String studentNumber) {
        try {
            return profileService.getStudentByStudentNumber(studentNumber);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
    }

    static boolean canSeeEmail(UUID ownerId, UUID viewerId, String viewerRoles) {
        if (viewerId != null && viewerId.equals(ownerId)) {
            return true;
        }
        return viewerRoles != null && Arrays.stream(viewerRoles.split(","))
                .map(String::trim)
                .anyMatch(ROLE_ADMIN::equals);
    }

    private UserProfileResponse findProfile(UUID userId) {
        try {
            return profileService.getUserProfile(userId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }
}
