package com.educonnect.courseservice.service;

import com.educonnect.courseservice.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StudentRelationService {

    private final CourseRepository courseRepository;

    public StudentRelationService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public boolean teachesStudent(UUID viewerId, UUID studentId) {
        return courseRepository.teachesStudent(viewerId, studentId);
    }
}
