package com.educonnect.courseservice.service;

import com.educonnect.common.web.ConflictException;
import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.exception.AlreadyEnrolledException;
import com.educonnect.courseservice.exception.CourseCapacityFullException;
import com.educonnect.courseservice.exception.DuplicateApplicationException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplication;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.model.CourseEnrollmentEvent;
import com.educonnect.courseservice.model.EnrollmentEventType;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.model.TermSeason;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseEnrollmentEventRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.CourseStaffRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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
    private final UUID termId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 10, 1);

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
    @Mock
    private CourseStaffRepository staffRepository;
    @Mock
    private TermService termService;
    @Mock
    private CourseEnrollmentEventRepository eventRepository;

    private CourseApplicationService service;

    private Course course;

    @BeforeEach
    void setUp() {
        service = new CourseApplicationService(applicationRepository, courseRepository, enrollmentRepository,
                userClient, courseCaches, new CourseStaffAccess(staffRepository), termService,
                new EnrollmentLedger(enrollmentRepository, eventRepository));
        course = new Course();
        course.setId(courseId);
        course.setTermId(termId);
        course.setInstructorId(instructorId);
        course.setCapacity(2);
    }

    @Test
    void applyToCourse_afterAnEarlierRejection_shouldOpenANewApplicationAndKeepTheOldOne() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        stubTerm(null, null);
        when(applicationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.applyToCourse(courseId, studentId);

        ArgumentCaptor<CourseApplication> saved = ArgumentCaptor.forClass(CourseApplication.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(CourseApplicationStatus.PENDING);
        verify(courseCaches).evictStaffCourses(course);
    }

    @Test
    void applyToCourse_whenApplicationPending_shouldRejectDuplicate() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(applicationRepository.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, CourseApplicationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.applyToCourse(courseId, studentId))
                .isInstanceOf(DuplicateApplicationException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyToCourse_outsideTheTermEnrollmentWindow_shouldFail() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        stubTerm(today.minusDays(20), today.minusDays(1));

        assertThatThrownBy(() -> service.applyToCourse(courseId, studentId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ENROLLMENT_CLOSED");
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
    void approveApplication_whenStudentWithdrewBefore_shouldReactivateEnrollmentAndRecordIt() {
        CourseApplication application = application(CourseApplicationStatus.PENDING);
        StudentCourseEnrollment withdrawn = new StudentCourseEnrollment(courseId, studentId);
        withdrawn.setId(UUID.randomUUID());
        withdrawn.withdraw(studentId, "Program çakışması", Instant.now());
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(courseRepository.findByIdForUpdate(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countActiveByCourseId(courseId)).thenReturn(1L);
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(withdrawn));

        service.approveApplication(applicationId, instructorId);

        ArgumentCaptor<StudentCourseEnrollment> saved = ArgumentCaptor.forClass(StudentCourseEnrollment.class);
        verify(enrollmentRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(withdrawn);
        assertThat(withdrawn.isActive()).isTrue();
        assertThat(withdrawn.getWithdrawnAt()).isNull();
        assertThat(application.getStatus()).isEqualTo(CourseApplicationStatus.APPROVED);
        ArgumentCaptor<CourseEnrollmentEvent> event = ArgumentCaptor.forClass(CourseEnrollmentEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getType()).isEqualTo(EnrollmentEventType.ENROLLED);
        assertThat(event.getValue().getActorId()).isEqualTo(instructorId);
        verify(courseCaches).evictStudentCourses(studentId);
        verify(courseCaches).evictStaffCourses(course);
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
        verify(eventRepository, never()).save(any());
    }

    private void stubTerm(LocalDate opensOn, LocalDate closesOn) {
        Term term = new Term(2027, TermSeason.FALL);
        term.schedule(today.minusDays(30), today.plusDays(90), opensOn, closesOn);
        when(termService.find(termId)).thenReturn(term);
        when(termService.today()).thenReturn(today);
    }

    private CourseApplication application(CourseApplicationStatus status) {
        CourseApplication application = new CourseApplication(courseId, studentId);
        application.setId(applicationId);
        application.setStatus(status);
        return application;
    }
}
