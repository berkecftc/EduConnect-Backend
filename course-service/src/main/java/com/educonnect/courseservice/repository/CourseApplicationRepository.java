package com.educonnect.courseservice.repository;

import com.educonnect.courseservice.model.CourseApplication;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseApplicationRepository extends JpaRepository<CourseApplication, UUID> {

    // Bir derse ait belirli statüdeki başvuruları tarih sırasına göre getir (FCFS)
    List<CourseApplication> findByCourseIdAndStatusOrderByApplicationDateAsc(UUID courseId, CourseApplicationStatus status);

    // Öğrenci bu derse zaten başvurmuş mu? (PENDING veya APPROVED)
    boolean existsByCourseIdAndStudentIdAndStatus(UUID courseId, UUID studentId, CourseApplicationStatus status);

    // Öğrencinin belirli bir derse başvurusunu bul
    Optional<CourseApplication> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    // Öğrencinin tüm başvurularını getir
    List<CourseApplication> findByStudentIdOrderByApplicationDateDesc(UUID studentId);

    // Bir derse ait belirli statüdeki başvuru sayısı
    long countByCourseIdAndStatus(UUID courseId, CourseApplicationStatus status);

    @Query("SELECT a.courseId AS courseId, COUNT(a) AS total FROM CourseApplication a "
            + "WHERE a.courseId IN :courseIds AND a.status = :status GROUP BY a.courseId")
    List<EnrollmentRepository.CourseCount> countByCourseIdsAndStatus(@Param("courseIds") Collection<UUID> courseIds,
                                                                     @Param("status") CourseApplicationStatus status);
}

