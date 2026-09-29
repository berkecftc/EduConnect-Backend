package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.RoleChangeRequestRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
class ClubPositionRules {

    private final ClubMembershipRepository membershipRepository;
    private final RoleChangeRequestRepository roleChangeRequestRepository;
    private final ClubAuthorizationService clubAuthorizationService;

    ClubPositionRules(ClubMembershipRepository membershipRepository,
                      RoleChangeRequestRepository roleChangeRequestRepository,
                      ClubAuthorizationService clubAuthorizationService) {
        this.membershipRepository = membershipRepository;
        this.roleChangeRequestRepository = roleChangeRequestRepository;
        this.clubAuthorizationService = clubAuthorizationService;
    }

    void ensureCapacity(UUID clubId, ClubPosition position, boolean includePending) {
        long holders = membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, position, true).size();
        long pending = includePending
                ? roleChangeRequestRepository.countByClubIdAndRequestedRoleAndStatus(
                        clubId, position, RoleChangeRequestStatus.PENDING)
                : 0;
        if (holders + pending >= position.maxActiveHolders()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, position.maxActiveHolders() == 1
                    ? "Bu görev dolu veya bu görev için bekleyen bir talep var."
                    : "Bu görev için en fazla " + position.maxActiveHolders() + " kişi olabilir.");
        }
    }

    void ensureNoManagementPositionElsewhere(UUID studentId, UUID clubId) {
        clubAuthorizationService.activeManagementPositionOf(studentId)
                .filter(membership -> !membership.getClubId().equals(clubId))
                .ifPresent(membership -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Bu öğrencinin başka bir kulüpte yönetim görevi var. "
                                    + "Bir öğrenci yalnızca bir kulüpte yönetim görevi alabilir.");
                });
    }
}
