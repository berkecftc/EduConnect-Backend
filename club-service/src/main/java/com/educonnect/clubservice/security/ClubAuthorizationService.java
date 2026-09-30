package com.educonnect.clubservice.security;

import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
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
                permissionsFor(position, actingPresident, advisor, club.getStatus()));
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
                        .filter(club -> !club.isClosed())
                        .ifPresent(club -> result.put(club.getId(), accessOf(club, userId)));
            }
        }
        for (Club club : clubRepository.findByAcademicAdvisorId(userId)) {
            if (club.isClosed()) {
                continue;
            }
            result.putIfAbsent(club.getId(), accessOf(club, userId));
        }
        return new ArrayList<>(result.values());
    }

    public Optional<ClubMembership> activeManagementPositionOf(UUID userId) {
        return membershipRepository.findByStudentId(userId).stream()
                .filter(ClubMembership::isActive)
                .filter(membership -> membership.getClubRole() != null && membership.getClubRole().isManagement())
                .filter(membership -> clubRepository.findById(membership.getClubId()).map(club -> !club.isClosed()).orElse(false))
                .findFirst();
    }

    private boolean isPresidencyVacant(UUID clubId) {
        return membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true).isEmpty();
    }

    static Set<ClubPermission> permissionsFor(ClubPosition position, boolean actingPresident, boolean advisor, ClubStatus status) {
        Set<ClubPermission> permissions = EnumSet.noneOf(ClubPermission.class);
        if (status == ClubStatus.CLOSED) {
            if (position != null || advisor) {
                permissions.add(ClubPermission.VIEW_MEMBERS);
            }
            if ((position != null && position.isManagement()) || advisor) {
                permissions.add(ClubPermission.VIEW_MANAGEMENT_DATA);
            }
            if ((position != null && (position.isBoard() || position == ClubPosition.AUDITOR)) || advisor) {
                permissions.add(ClubPermission.VIEW_DECISIONS);
                permissions.add(ClubPermission.VIEW_FINANCE);
            }
            return Set.copyOf(permissions);
        }
        if (position != null) {
            permissions.add(ClubPermission.VIEW_MEMBERS);
        }
        if (position != null && position.isManagement()) {
            permissions.add(ClubPermission.VIEW_MANAGEMENT_DATA);
        }
        if ((position != null && position.isBoard()) || position == ClubPosition.EVENT_COORDINATOR) {
            permissions.add(ClubPermission.MANAGE_EVENT_OPERATIONS);
        }
        if ((position != null && position.isBoard()) || position == ClubPosition.AUDITOR) {
            permissions.add(ClubPermission.VIEW_DECISIONS);
            permissions.add(ClubPermission.VIEW_FINANCE);
        }
        if (position == ClubPosition.TREASURER || actingPresident) {
            permissions.add(ClubPermission.PREPARE_FINANCE);
        }
        if (position == ClubPosition.SPONSORSHIP_OFFICER || actingPresident) {
            permissions.add(ClubPermission.PREPARE_SPONSORSHIP);
        }
        if (position == ClubPosition.VICE_PRESIDENT || position == ClubPosition.GENERAL_SECRETARY
                || position == ClubPosition.BOARD_MEMBER || position == ClubPosition.EVENT_COORDINATOR || actingPresident) {
            permissions.add(ClubPermission.PREPARE_EVENT);
        }
        if (position == ClubPosition.COMMUNICATIONS_OFFICER || actingPresident) {
            permissions.add(ClubPermission.PREPARE_PROFILE_CHANGE);
            permissions.add(ClubPermission.PREPARE_LOGO_CHANGE);
        }
        if (position == ClubPosition.GENERAL_SECRETARY) {
            permissions.add(ClubPermission.PREPARE_PROFILE_CHANGE);
        }
        if ((position != null && position.isBoard()) || position == ClubPosition.COMMUNICATIONS_OFFICER || actingPresident) {
            permissions.add(ClubPermission.PREPARE_ANNOUNCEMENT);
        }
        if (position == ClubPosition.MEMBERSHIP_OFFICER) {
            permissions.add(ClubPermission.REVIEW_MEMBERSHIP_REQUESTS);
        }
        if (position == ClubPosition.GENERAL_SECRETARY) {
            permissions.add(ClubPermission.MANAGE_MEMBERSHIP_REQUESTS);
        }
        if (actingPresident) {
            permissions.add(ClubPermission.MANAGE_MEMBERSHIP_REQUESTS);
            permissions.add(ClubPermission.PROPOSE_POSITION_CHANGE);
            permissions.add(ClubPermission.UPDATE_CLUB_PROFILE);
            permissions.add(ClubPermission.CREATE_EVENT);
            permissions.add(ClubPermission.PROPOSE_ADVISOR_CHANGE);
            permissions.add(ClubPermission.APPROVE_AS_PRESIDENT);
            permissions.add(ClubPermission.REQUEST_CLUB_CLOSURE);
        }
        if (advisor) {
            permissions.add(ClubPermission.VIEW_MEMBERS);
            permissions.add(ClubPermission.VIEW_MANAGEMENT_DATA);
            permissions.add(ClubPermission.VIEW_DECISIONS);
            permissions.add(ClubPermission.VIEW_FINANCE);
            permissions.add(ClubPermission.ADVISE);
        }
        return Set.copyOf(permissions);
    }
}
