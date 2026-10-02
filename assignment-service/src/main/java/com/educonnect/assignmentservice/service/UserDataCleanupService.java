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

    private static final String REASSIGN_GROUP_SUBMISSIONS = "UPDATE assignment_submissions s SET student_id = "
            + "(SELECT m.student_id FROM group_members m WHERE m.group_id = s.group_id AND m.student_id <> :userId "
            + "ORDER BY m.joined_at LIMIT 1) "
            + "WHERE s.student_id = :userId AND s.group_id IS NOT NULL AND EXISTS "
            + "(SELECT 1 FROM group_members m WHERE m.group_id = s.group_id AND m.student_id <> :userId)";

    @Transactional
    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#userId")
    public void deleteUserData(UUID userId) {
        entityManager.createNativeQuery(REASSIGN_GROUP_SUBMISSIONS).setParameter("userId", userId).executeUpdate();
        @SuppressWarnings("unchecked")
        List<String> files = entityManager.createNativeQuery(
                        "SELECT submission_file_url FROM assignment_submissions WHERE student_id = :userId AND submission_file_url IS NOT NULL "
                                + "UNION SELECT v.file_url FROM submission_versions v JOIN assignment_submissions s ON s.id = v.submission_id "
                                + "WHERE s.student_id = :userId AND v.file_url IS NOT NULL")
                .setParameter("userId", userId)
                .getResultList();
        int deleted = entityManager.createNativeQuery("DELETE FROM assignment_submissions WHERE student_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        for (String statement : List.of(
                "DELETE FROM assignment_extensions WHERE student_id = :userId",
                "DELETE FROM member_grades WHERE student_id = :userId",
                "UPDATE member_grades SET changed_by = NULL WHERE changed_by = :userId",
                "DELETE FROM grade_changes WHERE student_id = :userId",
                "UPDATE submission_versions SET submitted_by = NULL WHERE submitted_by = :userId",
                "DELETE FROM group_members WHERE student_id = :userId",
                "UPDATE group_members SET added_by = NULL WHERE added_by = :userId",
                "UPDATE group_sets SET created_by = NULL WHERE created_by = :userId",
                "UPDATE assignment_extensions SET granted_by = NULL WHERE granted_by = :userId",
                "UPDATE assignment_changes SET changed_by = NULL WHERE changed_by = :userId",
                "UPDATE grade_changes SET changed_by = NULL WHERE changed_by = :userId",
                "UPDATE assignments SET grades_published_by = NULL WHERE grades_published_by = :userId")) {
            entityManager.createNativeQuery(statement).setParameter("userId", userId).executeUpdate();
        }
        minioService.deleteFilesAfterCommit(files);
        log.info("Silinen kullanıcının ödev teslimleri temizlendi: userId={}, rows={}, files={}", userId, deleted, files.size());
    }
}
