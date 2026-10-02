package com.educonnect.userservice.service;

import com.educonnect.common.web.NotFoundException;
import com.educonnect.userservice.client.ClubRelationClient;
import com.educonnect.userservice.client.CourseRelationClient;
import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.dto.response.UserProfileResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ProfileViewService {

    private static final Logger log = LoggerFactory.getLogger(ProfileViewService.class);
    private static final Set<String> FULL_ACCESS_ROLES = Set.of("ROLE_ADMIN", "ROLE_STAFF");
    static final int MAX_BATCH_SIZE = 500;

    private final ProfileService profileService;
    private final ProfileAggregationService profileAggregationService;
    private final CourseRelationClient courseRelationClient;
    private final ClubRelationClient clubRelationClient;

    public ProfileViewService(ProfileService profileService,
                              ProfileAggregationService profileAggregationService,
                              CourseRelationClient courseRelationClient,
                              ClubRelationClient clubRelationClient) {
        this.profileService = profileService;
        this.profileAggregationService = profileAggregationService;
        this.courseRelationClient = courseRelationClient;
        this.clubRelationClient = clubRelationClient;
    }

    public UserProfileResponse getProfile(UUID userId, UUID viewerId, String viewerRoles) {
        UserProfileResponse profile = findProfile(userId);
        Visibility visibility = visibility(profile, viewerId, viewerRoles);
        if (visibility.email() && visibility.studentNumber()) {
            return profile;
        }
        UserProfileResponse visible = profile.withoutEmail();
        if (visibility.email()) {
            visible.setEmail(profile.getEmail());
        }
        if (!visibility.studentNumber()) {
            visible.setStudentNumber(null);
        }
        return visible;
    }

    public UserProfileResponseDTO getAggregatedProfile(UUID userId, UUID viewerId, String viewerRoles) {
        UserProfileResponse base = findProfile(userId);
        UserProfileResponseDTO profile = profileAggregationService.getAggregatedUserProfile(userId);
        Visibility visibility = visibility(base, viewerId, viewerRoles);
        if (!visibility.email()) {
            profile.setEmail(null);
        }
        if (!visibility.studentNumber()) {
            profile.setStudentNumber(null);
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
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
    }

    Visibility visibility(UserProfileResponse profile, UUID viewerId, String viewerRoles) {
        if (canSeeEverything(profile.getId(), viewerId, viewerRoles)) {
            return new Visibility(true, true);
        }
        boolean staffProfile = profile.getAffiliations() != null && profile.getAffiliations().contains(ProfileService.ACADEMICIAN_AFFILIATION);
        boolean studentProfile = profile.getStudentNumber() != null;
        boolean related = studentProfile && viewerId != null && related(viewerId, profile.getId());
        return new Visibility(staffProfile || related, related);
    }

    static boolean canSeeEverything(UUID ownerId, UUID viewerId, String viewerRoles) {
        if (viewerId != null && viewerId.equals(ownerId)) {
            return true;
        }
        return viewerRoles != null && Arrays.stream(viewerRoles.split(","))
                .map(String::trim)
                .anyMatch(FULL_ACCESS_ROLES::contains);
    }

    private boolean related(UUID viewerId, UUID studentId) {
        return relation(() -> courseRelationClient.teachesStudent(viewerId, studentId))
                || relation(() -> clubRelationClient.managesStudent(viewerId, studentId));
    }

    private static boolean relation(Supplier<Map<String, Boolean>> lookup) {
        try {
            Map<String, Boolean> result = lookup.get();
            return result != null && Boolean.TRUE.equals(result.get("related"));
        } catch (RuntimeException e) {
            log.warn("Relation lookup failed; contact details stay hidden: {}", e.getMessage());
            return false;
        }
    }

    private UserProfileResponse findProfile(UUID userId) {
        try {
            return profileService.getUserProfile(userId);
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }

    record Visibility(boolean email, boolean studentNumber) {
    }
}
