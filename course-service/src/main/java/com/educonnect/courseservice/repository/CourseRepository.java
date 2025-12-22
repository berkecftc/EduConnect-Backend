package com.educonnect.courseservice.repository;
import com.educonnect.courseservice.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByInstructorId(UUID instructorId);
    boolean existsByCode(String code);
}