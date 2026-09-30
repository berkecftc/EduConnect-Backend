package com.educonnect.courseservice.service;

import com.educonnect.courseservice.event.CourseEvent;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CourseRemoval {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseProducer courseProducer;
    private final CourseCaches courseCaches;

    public CourseRemoval(CourseRepository courseRepository,
                         EnrollmentRepository enrollmentRepository,
                         CourseProducer courseProducer,
                         CourseCaches courseCaches) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseProducer = courseProducer;
        this.courseCaches = courseCaches;
    }

    @Transactional
    public void remove(Course course) {
        courseCaches.evictStudentCourses(enrollmentRepository.findByCourseIdAndIsActive(course.getId(), true).stream()
                .map(StudentCourseEnrollment::getStudentId)
                .toList());
        courseRepository.delete(course);
        courseProducer.sendCourseDeletedEvent(new CourseEvent(course.getId(), course.getTitle(), course.getCode(), "DELETED"));
        courseCaches.evictInstructorCourses(course.getInstructorId());
    }
}
