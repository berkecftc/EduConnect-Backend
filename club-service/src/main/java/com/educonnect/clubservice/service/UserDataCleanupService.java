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
            "DELETE FROM club_position_terms WHERE student_id = :userId",
            "DELETE FROM club_founders WHERE student_id = :userId",
            "DELETE FROM club_election_ballots_cast WHERE voter_id = :userId",
            "DELETE FROM club_election_candidates WHERE student_id = :userId",
            "UPDATE club_elections SET opened_by = NULL WHERE opened_by = :userId",
            "DELETE FROM club_meeting_attendees WHERE student_id = :userId",
            "DELETE FROM club_memberships WHERE student_id = :userId",
            "DELETE FROM club_membership_requests WHERE student_id = :userId",
            "UPDATE club_membership_requests SET processed_by = NULL WHERE processed_by = :userId",
            "DELETE FROM club_approval_requests WHERE subject_user_id = :userId AND type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'MEMBER_EXPULSION')",
            "UPDATE club_approval_requests SET prepared_by = NULL WHERE prepared_by = :userId",
            "UPDATE club_approval_requests SET president_decided_by = NULL WHERE president_decided_by = :userId",
            "UPDATE club_approval_requests SET decided_by = NULL WHERE decided_by = :userId",
            "UPDATE club_decision_log SET actor_id = NULL WHERE actor_id = :userId",
            "UPDATE club_decision_log SET subject_user_id = NULL WHERE subject_user_id = :userId",
            "DELETE FROM club_creation_requests WHERE requesting_student_id = :userId",
            "UPDATE club_creation_requests SET suggested_advisor_id = NULL WHERE suggested_advisor_id = :userId",
            "UPDATE club_creation_requests SET processed_by = NULL WHERE processed_by = :userId",
            "UPDATE archived_clubs SET academic_advisor_id = NULL WHERE academic_advisor_id = :userId",
            "UPDATE archived_clubs SET deleted_by_admin_id = NULL WHERE deleted_by_admin_id = :userId",
            "UPDATE clubs SET academic_advisor_id = NULL, status = CASE WHEN status = 'CLOSED' THEN status ELSE 'AWAITING_ADVISOR' END WHERE academic_advisor_id = :userId",
            "UPDATE clubs SET closed_by = NULL WHERE closed_by = :userId",
            "UPDATE club_announcements SET prepared_by = NULL WHERE prepared_by = :userId",
            "UPDATE club_announcements SET removed_by = NULL WHERE removed_by = :userId",
            "UPDATE club_budgets SET prepared_by = NULL WHERE prepared_by = :userId",
            "UPDATE club_finance_entries SET prepared_by = NULL WHERE prepared_by = :userId",
            "UPDATE club_sponsorships SET prepared_by = NULL WHERE prepared_by = :userId",
            "UPDATE club_meetings SET prepared_by = NULL WHERE prepared_by = :userId",
            "UPDATE club_reports SET created_by = NULL WHERE created_by = :userId",
            "UPDATE club_reports SET updated_by = NULL WHERE updated_by = :userId");

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
