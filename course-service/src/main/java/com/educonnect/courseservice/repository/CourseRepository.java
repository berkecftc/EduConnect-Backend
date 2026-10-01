package com.educonnect.courseservice.repository;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.model.CourseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    @Query("SELECT c FROM Course c WHERE c.instructorId = :userId "
            + "OR c.id IN (SELECT s.courseId FROM CourseStaff s WHERE s.userId = :userId)")
    List<Course> findByStaffMember(@Param("userId") UUID userId);

    @Query("SELECT c FROM Course c WHERE c.status <> :hidden AND (c.instructorId = :userId "
            + "OR c.id IN (SELECT s.courseId FROM CourseStaff s WHERE s.userId = :userId AND s.role = :role))")
    List<Course> findTaughtBy(@Param("userId") UUID userId, @Param("role") CourseStaffRole role,
                              @Param("hidden") CourseStatus hidden);
    boolean existsByCatalogCourseIdAndTermIdAndSection(UUID catalogCourseId, UUID termId, String section);
    boolean existsByTermId(UUID termId);
    List<Course> findByTermId(UUID termId);
    List<Course> findByStatusIn(Collection<CourseStatus> statuses);
    List<Course> findByStatusNot(CourseStatus status);
    Page<Course> findByStatusNot(CourseStatus status, Pageable pageable);
    List<Course> findByTermIdAndStatusNot(UUID termId, CourseStatus status);
    Page<Course> findByTermIdAndStatusNot(UUID termId, CourseStatus status, Pageable pageable);
    Page<Course> findByTermId(UUID termId, Pageable pageable);
    boolean existsByImageUrl(String imageUrl);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") UUID id);
}
