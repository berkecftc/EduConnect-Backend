package com.educonnect.clubservice.security;

import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClubAuthorizationService {

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;

    public ClubAuthorizationService(ClubRepository clubRepository, ClubMembershipRepository membershipRepository) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
    }

    public ClubAccess accessOf(UUID clubId, UUID userId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        return accessOf(club, userId);
    }

    public ClubAccess accessOf(Club club, UUID userId) {
        if (userId == null) {
            return new ClubAccess(club.getId(), null, null, false, false, Set.of());
        }
        ClubPosition position = membershipRepository.findByClubIdAndStudentId(club.getId(), userId)
                .filter(ClubMembership::isActive)
                .map(ClubMembership::getClubRole)
                .orElse(null);
        boolean advisor = userId.equals(club.getAcademicAdvisorId());
        boolean actingPresident = position == ClubPosition.PRESIDENT
                || (position == ClubPosition.VICE_PRESIDENT && isPresidencyVacant(club.getId()));
        return new ClubAccess(club.getId(), userId, position, actingPresident, advisor,
                permissionsFor(position, actingPresident, advisor));
    }

    public ClubAccess require(UUID clubId, UUID userId, ClubPermission permission) {
        ClubAccess access = accessOf(clubId, userId);
        if (!access.has(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için kulüpte yetkiniz yok.");
        }
        return access;
    }

    public List<ClubAccess> accessesOf(UUID userId) {
        Map<UUID, ClubAccess> result = new LinkedHashMap<>();
        for (ClubMembership membership : membershipRepository.findByStudentId(userId)) {
            if (membership.isActive() && membership.getClubRole() != null && membership.getClubRole().isManagement()) {
                clubRepository.findById(membership.getClubId())
                        .ifPresent(club -> result.put(club.getId(), accessOf(club, userId)));
            }
        }
        for (Club club : clubRepository.findByAcademicAdvisorId(userId)) {
            result.putIfAbsent(club.getId(), accessOf(club, userId));
        }
        return new ArrayList<>(result.values());
    }

    public Optional<ClubMembership> activeManagementPositionOf(UUID userId) {
        return membershipRepository.findByStudentId(userId).stream()
                .filter(ClubMembership::isActive)
                .filter(membership -> membership.getClubRole() != null && membership.getClubRole().isManagement())
                .findFirst();
    }

    private boolean isPresidencyVacant(UUID clubId) {
        return membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true).isEmpty();
    }

    static Set<ClubPermission> permissionsFor(ClubPosition position, boolean actingPresident, boolean advisor) {
        Set<ClubPermission> permissions = EnumSet.noneOf(ClubPermission.class);
        if (position != null) {
            permissions.add(ClubPermission.VIEW_MEMBERS);
        }
        if (position != null && position.isManagement()) {
            permissions.add(ClubPermission.VIEW_MANAGEMENT_DATA);
            permissions.add(ClubPermission.MANAGE_EVENT_OPERATIONS);
        }
        if (position == ClubPosition.GENERAL_SECRETARY) {
            permissions.add(ClubPermission.MANAGE_MEMBERSHIP_REQUESTS);
        }
        if (actingPresident) {
            permissions.add(ClubPermission.MANAGE_MEMBERSHIP_REQUESTS);
            permissions.add(ClubPermission.PROPOSE_POSITION_CHANGE);
            permissions.add(ClubPermission.UPDATE_CLUB_PROFILE);
            permissions.add(ClubPermission.CREATE_EVENT);
        }
        if (advisor) {
            permissions.add(ClubPermission.VIEW_MEMBERS);
            permissions.add(ClubPermission.VIEW_MANAGEMENT_DATA);
            permissions.add(ClubPermission.ADVISE);
        }
        return Set.copyOf(permissions);
    }
}
