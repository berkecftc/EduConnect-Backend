package com.educonnect.clubservice.service;

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
            "UPDATE archived_clubs SET deleted_by_admin_id = NULL WHERE deleted_by_admin_id = :userId");

    @PersistenceContext
    private EntityManager entityManager;

    private final ClubCacheEvictor clubCacheEvictor;

    public UserDataCleanupService(ClubCacheEvictor clubCacheEvictor) {
        this.clubCacheEvictor = clubCacheEvictor;
    }

    @Transactional
    public void deleteUserData(UUID userId) {
        int affected = 0;
        for (String statement : STATEMENTS) {
            affected += entityManager.createNativeQuery(statement).setParameter("userId", userId).executeUpdate();
        }
        clubCacheEvictor.evictUser(userId);
        log.info("Silinen kullanıcının kulüp verisi temizlendi: userId={}, rows={}", userId, affected);
    }
}
