package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.exception.UnauthorizedCourseAccessException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseAnnouncementRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseAnnouncementServiceTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @Mock
    private CourseAnnouncementRepository announcementRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CourseProducer courseProducer;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private CourseAnnouncementService announcementService;

    @BeforeEach
    void setUp() {
        Course course = new Course();
        course.setId(courseId);
        course.setInstructorId(instructorId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
    }

    @Test
    void getAnnouncementsByCourse_whenViewerIsInstructor_shouldReturnAnnouncements() {
        when(announcementRepository.findByCourseIdOrderByCreatedAtDesc(courseId)).thenReturn(List.of());

        assertThat(announcementService.getAnnouncementsByCourse(courseId, instructorId, false)).isEmpty();
    }

    @Test
    void getAnnouncementsByCourse_whenViewerIsEnrolledStudent_shouldReturnAnnouncements() {
        when(enrollmentRepository.existsByCourseIdAndStudentIdAndIsActive(courseId, studentId, true)).thenReturn(true);
        when(announcementRepository.findByCourseIdOrderByCreatedAtDesc(courseId)).thenReturn(List.of());

        assertThat(announcementService.getAnnouncementsByCourse(courseId, studentId, false)).isEmpty();
    }

    @Test
    void getAnnouncementsByCourse_whenViewerIsAdmin_shouldReturnAnnouncements() {
        when(announcementRepository.findByCourseIdOrderByCreatedAtDesc(courseId)).thenReturn(List.of());

        assertThat(announcementService.getAnnouncementsByCourse(courseId, UUID.randomUUID(), true)).isEmpty();
    }

    @Test
    void getAnnouncementsByCourse_whenViewerIsNotEnrolled_shouldThrowForbidden() {
        UUID outsider = UUID.randomUUID();
        when(enrollmentRepository.existsByCourseIdAndStudentIdAndIsActive(courseId, outsider, true)).thenReturn(false);

        assertThatThrownBy(() -> announcementService.getAnnouncementsByCourse(courseId, outsider, false))
                .isInstanceOf(UnauthorizedCourseAccessException.class);
        verify(announcementRepository, never()).findByCourseIdOrderByCreatedAtDesc(courseId);
    }
}
