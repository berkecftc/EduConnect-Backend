package com.educonnect.gamificationservice.service;

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
            "DELETE FROM user_badges WHERE user_id = :userId",
            "DELETE FROM point_history WHERE user_id = :userId",
            "DELETE FROM user_reputation WHERE user_id = :userId");

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void deleteUserData(UUID userId) {
        int affected = 0;
        for (String statement : STATEMENTS) {
            affected += entityManager.createNativeQuery(statement).setParameter("userId", userId).executeUpdate();
        }
        log.info("Silinen kullanıcının puan ve rozetleri temizlendi: userId={}, rows={}", userId, affected);
    }
}
