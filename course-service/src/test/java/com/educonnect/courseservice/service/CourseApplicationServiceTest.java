package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.exception.AlreadyEnrolledException;
import com.educonnect.courseservice.exception.CourseCapacityFullException;
import com.educonnect.courseservice.exception.DuplicateApplicationException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplication;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseApplicationServiceTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();

    @Mock
    private CourseApplicationRepository applicationRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private UserClient userClient;
    @Mock
    private CourseCaches courseCaches;

    @InjectMocks
    private CourseApplicationService service;

    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(courseId);
        course.setInstructorId(instructorId);
        course.setCapacity(2);
    }

    @Test
    void applyToCourse_whenPreviouslyRejected_shouldReopenSameApplication() {
        CourseApplication rejected = application(CourseApplicationStatus.REJECTED);
        rejected.setProcessedBy(instructorId);
        rejected.setProcessedDate(LocalDateTime.now().minusDays(1));
        rejected.setRejectionReason("Kontenjan");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(applicationRepository.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(rejected));
        when(applicationRepository.save(rejected)).thenReturn(rejected);

        service.applyToCourse(courseId, studentId);

        assertThat(rejected.getStatus()).isEqualTo(CourseApplicationStatus.PENDING);
        assertThat(rejected.getProcessedBy()).isNull();
        assertThat(rejected.getProcessedDate()).isNull();
        assertThat(rejected.getRejectionReason()).isNull();
        verify(courseCaches).evictInstructorCourses(instructorId);
    }

    @Test
    void applyToCourse_whenApplicationPending_shouldRejectDuplicate() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(applicationRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(Optional.of(application(CourseApplicationStatus.PENDING)));

        assertThatThrownBy(() -> service.applyToCourse(courseId, studentId))
                .isInstanceOf(DuplicateApplicationException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void approveApplication_shouldLockCourseAndRejectWhenCapacityFull() {
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application(CourseApplicationStatus.PENDING)));
        when(courseRepository.findByIdForUpdate(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countActiveByCourseId(courseId)).thenReturn(2L);

        assertThatThrownBy(() -> service.approveApplication(applicationId, instructorId))
                .isInstanceOf(CourseCapacityFullException.class);
        verify(courseRepository, never()).findById(any());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void approveApplication_whenStudentWithdrewBefore_shouldReactivateEnrollment() {
        CourseApplication application = application(CourseApplicationStatus.PENDING);
        StudentCourseEnrollment withdrawn = new StudentCourseEnrollment(courseId, studentId);
        withdrawn.setId(UUID.randomUUID());
        withdrawn.setActive(false);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(courseRepository.findByIdForUpdate(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countActiveByCourseId(courseId)).thenReturn(1L);
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(withdrawn));

        service.approveApplication(applicationId, instructorId);

        ArgumentCaptor<StudentCourseEnrollment> saved = ArgumentCaptor.forClass(StudentCourseEnrollment.class);
        verify(enrollmentRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(withdrawn);
        assertThat(withdrawn.isActive()).isTrue();
        assertThat(application.getStatus()).isEqualTo(CourseApplicationStatus.APPROVED);
        verify(courseCaches).evictStudentCourses(studentId);
        verify(courseCaches).evictInstructorCourses(instructorId);
    }

    @Test
    void approveApplication_whenAlreadyActivelyEnrolled_shouldFail() {
        StudentCourseEnrollment active = new StudentCourseEnrollment(courseId, studentId);
        active.setId(UUID.randomUUID());
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application(CourseApplicationStatus.PENDING)));
        when(courseRepository.findByIdForUpdate(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countActiveByCourseId(courseId)).thenReturn(1L);
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.approveApplication(applicationId, instructorId))
                .isInstanceOf(AlreadyEnrolledException.class);
    }

    private CourseApplication application(CourseApplicationStatus status) {
        CourseApplication application = new CourseApplication(courseId, studentId);
        application.setId(applicationId);
        application.setStatus(status);
        return application;
    }
}
