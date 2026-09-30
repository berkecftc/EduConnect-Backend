package com.educonnect.courseservice.repository;
import com.educonnect.courseservice.model.Course;
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
    List<Course> findByInstructorId(UUID instructorId);
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
