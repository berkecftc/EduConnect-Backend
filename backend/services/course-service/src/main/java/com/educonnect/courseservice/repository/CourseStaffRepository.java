package com.educonnect.courseservice.repository;

import com.educonnect.courseservice.model.CourseStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseStaffRepository extends JpaRepository<CourseStaff, UUID> {
    Optional<CourseStaff> findByCourseIdAndUserId(UUID courseId, UUID userId);
    List<CourseStaff> findByCourseIdOrderByCreatedAtAsc(UUID courseId);
    List<CourseStaff> findByCourseIdIn(Collection<UUID> courseIds);
    List<CourseStaff> findByUserId(UUID userId);
    boolean existsByCourseIdAndUserId(UUID courseId, UUID userId);
}
