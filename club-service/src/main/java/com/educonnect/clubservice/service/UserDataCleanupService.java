package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserDataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UserDataCleanupService.class);

    private static final List<String> STATEMENTS = List.of(
            "DELETE FROM club_memberships WHERE student_id = :userId",
            "DELETE FROM club_membership_requests WHERE student_id = :userId",
            "UPDATE club_membership_requests SET processed_by = NULL WHERE processed_by = :userId",
            "DELETE FROM role_change_requests WHERE student_id = :userId",
            "UPDATE role_change_requests SET requester_id = NULL WHERE requester_id = :userId",
            "UPDATE role_change_requests SET processed_by = NULL WHERE processed_by = :userId",
            "DELETE FROM club_creation_requests WHERE requesting_student_id = :userId",
            "UPDATE club_creation_requests SET suggested_advisor_id = NULL WHERE suggested_advisor_id = :userId",
            "UPDATE club_creation_requests SET processed_by = NULL WHERE processed_by = :userId",
            "UPDATE archived_clubs SET academic_advisor_id = NULL WHERE academic_advisor_id = :userId",
            "UPDATE archived_clubs SET deleted_by_admin_id = NULL WHERE deleted_by_admin_id = :userId",
            "UPDATE clubs SET academic_advisor_id = NULL, status = CASE WHEN status = 'CLOSED' THEN status ELSE 'AWAITING_ADVISOR' END WHERE academic_advisor_id = :userId",
            "UPDATE clubs SET closed_by = NULL WHERE closed_by = :userId",
            "DELETE FROM advisor_change_requests WHERE proposed_advisor_id = :userId",
            "UPDATE advisor_change_requests SET previous_advisor_id = NULL WHERE previous_advisor_id = :userId",
            "UPDATE advisor_change_requests SET requested_by = NULL WHERE requested_by = :userId");

    @PersistenceContext
    private EntityManager entityManager;

    private final ClubCacheEvictor clubCacheEvictor;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubLeadershipService leadershipService;
    private final AdvisorChangeService advisorChangeService;

    public UserDataCleanupService(ClubCacheEvictor clubCacheEvictor,
                                  ClubMembershipRepository membershipRepository,
                                  ClubRepository clubRepository,
                                  ClubLeadershipService leadershipService,
                                  AdvisorChangeService advisorChangeService) {
        this.clubCacheEvictor = clubCacheEvictor;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.leadershipService = leadershipService;
        this.advisorChangeService = advisorChangeService;
    }

    @Transactional
    public void deleteUserData(UUID userId) {
        List<UUID> presidedClubs = membershipRepository
                .findByStudentIdAndClubRoleAndIsActive(userId, ClubPosition.PRESIDENT, true).stream()
                .map(ClubMembership::getClubId)
                .toList();
        List<UUID> advisedClubs = clubRepository.findByAcademicAdvisorId(userId).stream()
                .filter(club -> !club.isClosed())
                .map(Club::getId)
                .toList();
        int affected = 0;
        for (String statement : STATEMENTS) {
            affected += entityManager.createNativeQuery(statement).setParameter("userId", userId).executeUpdate();
        }
        clubCacheEvictor.evictUser(userId);
        entityManager.flush();
        entityManager.clear();
        presidedClubs.forEach(leadershipService::handlePresidencyVacancy);
        advisorChangeService.advisorLeft(advisedClubs);
        log.info("Silinen kullanıcının kulüp verisi temizlendi: userId={}, rows={}", userId, affected);
    }
}
