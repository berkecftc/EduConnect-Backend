package com.educonnect.courseservice.repository;

import com.educonnect.courseservice.model.StudentCourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<StudentCourseEnrollment, UUID> {

    // Öğrencinin kayıtlı olduğu tüm kursları getir
    List<StudentCourseEnrollment> findByStudentIdAndIsActive(UUID studentId, boolean isActive);

    // Bir kursa kayıtlı tüm öğrencileri getir
    List<StudentCourseEnrollment> findByCourseIdAndIsActive(UUID courseId, boolean isActive);

    // Öğrenci bu kursa kayıtlı mı kontrol et
    Optional<StudentCourseEnrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    // Bir kursa kayıtlı aktif öğrenci sayısı
    @Query("SELECT COUNT(e) FROM StudentCourseEnrollment e WHERE e.courseId = :courseId AND e.isActive = true")
    long countActiveByCourseId(UUID courseId);

    @Query("SELECT e.courseId AS courseId, COUNT(e) AS total FROM StudentCourseEnrollment e "
            + "WHERE e.courseId IN :courseIds AND e.isActive = true GROUP BY e.courseId")
    List<CourseCount> countActiveByCourseIds(@Param("courseIds") Collection<UUID> courseIds);

    interface CourseCount {
        UUID getCourseId();
        long getTotal();
    }

    // Öğrenci bu kursa aktif olarak kayıtlı mı
    boolean existsByCourseIdAndStudentIdAndIsActive(UUID courseId, UUID studentId, boolean isActive);
}

