package com.educonnect.courseservice.repository;

import com.educonnect.courseservice.model.CourseAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement, UUID> {

    List<CourseAnnouncement> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
}

