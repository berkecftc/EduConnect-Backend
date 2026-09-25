package com.educonnect.assignmentservice.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserDataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UserDataCleanupService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#userId")
    public void deleteUserData(UUID userId) {
        int deleted = entityManager.createNativeQuery("DELETE FROM assignment_submissions WHERE student_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        log.info("Silinen kullanıcının ödev teslimleri temizlendi: userId={}, rows={}", userId, deleted);
    }
}
