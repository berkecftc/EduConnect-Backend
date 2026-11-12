package com.educonnect.eventservice.service;

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
            "DELETE FROM event_registrations WHERE student_id = :userId",
            "DELETE FROM event_participation_requests WHERE student_id = :userId",
            "UPDATE event_participation_requests SET processed_by = NULL WHERE processed_by = :userId",
            "UPDATE events SET created_by_student_id = NULL WHERE created_by_student_id = :userId");

    @PersistenceContext
    private EntityManager entityManager;

    private final EventCaches eventCaches;

    public UserDataCleanupService(EventCaches eventCaches) {
        this.eventCaches = eventCaches;
    }

    @Transactional
    public void deleteUserData(UUID userId) {
        int affected = 0;
        for (String statement : STATEMENTS) {
            affected += entityManager.createNativeQuery(statement).setParameter("userId", userId).executeUpdate();
        }
        eventCaches.evictUser(userId);
        log.info("Silinen kullanıcının etkinlik verisi temizlendi: userId={}, rows={}", userId, affected);
    }
}
