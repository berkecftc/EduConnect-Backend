package com.educonnect.courseservice.service;

import com.educonnect.courseservice.event.CourseEvent;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseMaterial;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseMaterialRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Component
public class CourseRemoval {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseProducer courseProducer;
    private final CourseCaches courseCaches;
    private final CourseMaterialRepository materialRepository;
    private final MinioService minioService;

    public CourseRemoval(CourseRepository courseRepository,
                         EnrollmentRepository enrollmentRepository,
                         CourseProducer courseProducer,
                         CourseCaches courseCaches,
                         CourseMaterialRepository materialRepository,
                         MinioService minioService) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseProducer = courseProducer;
        this.courseCaches = courseCaches;
        this.materialRepository = materialRepository;
        this.minioService = minioService;
    }

    @Transactional
    public void remove(Course course) {
        courseCaches.evictStudentCourses(enrollmentRepository.findByCourseIdAndIsActive(course.getId(), true).stream()
                .map(StudentCourseEnrollment::getStudentId)
                .toList());
        courseCaches.evictStaffCourses(course);
        List<String> materialFiles = materialRepository.findByCourseId(course.getId()).stream()
                .map(CourseMaterial::getFileUrl)
                .filter(Objects::nonNull)
                .toList();
        courseRepository.delete(course);
        minioService.deleteFilesAfterCommit(materialFiles);
        courseProducer.sendCourseDeletedEvent(new CourseEvent(course.getId(), course.getTitle(), course.getCode(), "DELETED"));
    }
}
