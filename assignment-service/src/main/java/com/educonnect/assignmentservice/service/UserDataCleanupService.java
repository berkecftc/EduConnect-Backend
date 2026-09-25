package com.educonnect.assignmentservice.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserDataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UserDataCleanupService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final MinioService minioService;

    public UserDataCleanupService(MinioService minioService) {
        this.minioService = minioService;
    }

    @Transactional
    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#userId")
    public void deleteUserData(UUID userId) {
        @SuppressWarnings("unchecked")
        List<String> files = entityManager.createNativeQuery(
                        "SELECT submission_file_url FROM assignment_submissions WHERE student_id = :userId AND submission_file_url IS NOT NULL")
                .setParameter("userId", userId)
                .getResultList();
        int deleted = entityManager.createNativeQuery("DELETE FROM assignment_submissions WHERE student_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        minioService.deleteFilesAfterCommit(files);
        log.info("Silinen kullanıcının ödev teslimleri temizlendi: userId={}, rows={}, files={}", userId, deleted, files.size());
    }
}
