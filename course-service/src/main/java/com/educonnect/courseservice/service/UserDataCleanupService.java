package com.educonnect.courseservice.service;

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

    private static final String AFFECTED_STAFF = "WITH affected AS ("
            + "SELECT course_id FROM student_course_enrollments WHERE student_id = :userId "
            + "UNION SELECT course_id FROM course_applications WHERE student_id = :userId "
            + "UNION SELECT course_id FROM course_staff WHERE user_id = :userId) "
            + "SELECT CAST(c.instructor_id AS varchar) FROM courses c WHERE c.id IN (SELECT course_id FROM affected) "
            + "UNION SELECT CAST(s.user_id AS varchar) FROM course_staff s WHERE s.course_id IN (SELECT course_id FROM affected)";

    private static final List<String> STATEMENTS = List.of(
            "DELETE FROM student_course_enrollments WHERE student_id = :userId",
            "DELETE FROM course_applications WHERE student_id = :userId",
            "DELETE FROM course_enrollment_events WHERE student_id = :userId",
            "UPDATE course_enrollment_events SET actor_id = NULL WHERE actor_id = :userId",
            "UPDATE student_course_enrollments SET withdrawn_by = NULL WHERE withdrawn_by = :userId",
            "DELETE FROM course_staff WHERE user_id = :userId",
            "UPDATE course_staff SET added_by = NULL WHERE added_by = :userId",
            "UPDATE course_applications SET processed_by = NULL WHERE processed_by = :userId",
            "UPDATE course_announcements SET created_by = NULL WHERE created_by = :userId",
            "UPDATE course_materials SET created_by = NULL WHERE created_by = :userId");

    @PersistenceContext
    private EntityManager entityManager;

    private final CourseCaches courseCaches;

    public UserDataCleanupService(CourseCaches courseCaches) {
        this.courseCaches = courseCaches;
    }

    @Transactional
    public void deleteUserData(UUID userId) {
        @SuppressWarnings("unchecked")
        List<String> instructorIds = entityManager.createNativeQuery(AFFECTED_STAFF)
                .setParameter("userId", userId)
                .getResultList();
        int affected = 0;
        for (String statement : STATEMENTS) {
            affected += entityManager.createNativeQuery(statement).setParameter("userId", userId).executeUpdate();
        }
        courseCaches.evictStudentCourses(userId);
        courseCaches.evictInstructorCourses(userId);
        instructorIds.stream().map(UUID::fromString).forEach(courseCaches::evictInstructorCourses);
        log.info("Silinen kullanıcının ders verisi temizlendi: userId={}, rows={}", userId, affected);
    }
}
