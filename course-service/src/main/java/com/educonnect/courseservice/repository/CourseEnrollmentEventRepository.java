package com.educonnect.courseservice.repository;

import com.educonnect.courseservice.model.CourseEnrollmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseEnrollmentEventRepository extends JpaRepository<CourseEnrollmentEvent, UUID> {
    List<CourseEnrollmentEvent> findByCourseIdOrderByOccurredAtDesc(UUID courseId);
    List<CourseEnrollmentEvent> findByStudentIdOrderByOccurredAtDesc(UUID studentId);
}
