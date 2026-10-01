package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.client.UserLookup;
import com.educonnect.courseservice.dto.EnrollmentEventResponse;
import com.educonnect.courseservice.dto.UserSummaryDto;
import com.educonnect.courseservice.exception.CourseNotFoundException;
import com.educonnect.courseservice.exception.EnrollmentNotFoundException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseEnrollmentEvent;
import com.educonnect.courseservice.model.EnrollmentEventType;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.repository.CourseEnrollmentEventRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import com.educonnect.courseservice.repository.TermRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class CourseEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(CourseEnrollmentService.class);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseEnrollmentEventRepository eventRepository;
    private final TermRepository termRepository;
    private final EnrollmentLedger enrollmentLedger;
    private final CourseStaffAccess staffAccess;
    private final CourseCaches courseCaches;
    private final UserClient userClient;

    public CourseEnrollmentService(CourseRepository courseRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   CourseEnrollmentEventRepository eventRepository,
                                   TermRepository termRepository,
                                   EnrollmentLedger enrollmentLedger,
                                   CourseStaffAccess staffAccess,
                                   CourseCaches courseCaches,
                                   UserClient userClient) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.eventRepository = eventRepository;
        this.termRepository = termRepository;
        this.enrollmentLedger = enrollmentLedger;
        this.staffAccess = staffAccess;
        this.courseCaches = courseCaches;
        this.userClient = userClient;
    }

    public EnrollmentEventResponse withdraw(UUID courseId, UUID studentId, String reason) {
        Course course = find(courseId);
        StudentCourseEnrollment enrollment = activeEnrollment(courseId, studentId, "Bu derse kayıtlı değilsiniz.");
        CourseLifecycleService.requireRunning(course);
        CourseEnrollmentEvent event = enrollmentLedger.end(enrollment, EnrollmentEventType.WITHDRAWN, studentId, normalize(reason));
        evict(course, studentId);
        log.info("Student withdrew: course={}, student={}", courseId, studentId);
        return responses(List.of(event), Map.of(course.getId(), course)).getFirst();
    }

    public EnrollmentEventResponse remove(UUID courseId, UUID actorId, UUID studentId, String reason) {
        Course course = find(courseId);
        staffAccess.requireTeacher(course, actorId, "Öğrenciyi dersten yalnızca koordinatör veya hoca çıkarabilir.");
        StudentCourseEnrollment enrollment = activeEnrollment(courseId, studentId, "Öğrenci bu derse kayıtlı değil.");
        CourseLifecycleService.requireRunning(course);
        CourseEnrollmentEvent event = enrollmentLedger.end(enrollment, EnrollmentEventType.REMOVED, actorId, normalize(reason));
        evict(course, studentId);
        log.info("Student removed: course={}, student={}, by={}", courseId, studentId, actorId);
        return responses(List.of(event), Map.of(course.getId(), course)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentEventResponse> courseHistory(UUID courseId, UUID userId) {
        Course course = find(courseId);
        staffAccess.requireStaff(course, userId, "Bu dersin kadrosunda değilsiniz.");
        return responses(eventRepository.findByCourseIdOrderByOccurredAtDesc(courseId), Map.of(course.getId(), course));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentEventResponse> studentHistory(UUID studentId) {
        List<CourseEnrollmentEvent> events = eventRepository.findByStudentIdOrderByOccurredAtDesc(studentId);
        Map<UUID, Course> courses = courseRepository.findAllById(events.stream().map(CourseEnrollmentEvent::getCourseId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        return responses(events, courses);
    }

    private List<EnrollmentEventResponse> responses(List<CourseEnrollmentEvent> events, Map<UUID, Course> courses) {
        if (events.isEmpty()) {
            return List.of();
        }
        Map<UUID, Term> terms = termRepository.findAllById(courses.values().stream().map(Course::getTermId).distinct().toList())
                .stream().collect(Collectors.toMap(Term::getId, Function.identity()));
        Map<UUID, UserSummaryDto> students = UserLookup.usersById(userClient,
                events.stream().map(CourseEnrollmentEvent::getStudentId).toList());
        ZoneId zone = ZoneId.systemDefault();
        return events.stream().map(event -> {
            Course course = courses.get(event.getCourseId());
            Term term = course != null ? terms.get(course.getTermId()) : null;
            UserSummaryDto student = students.get(event.getStudentId());
            boolean late = term != null && event.getType() != EnrollmentEventType.ENROLLED
                    && term.isAfterEnrollment(event.getOccurredAt().atZone(zone).toLocalDate());
            return new EnrollmentEventResponse(event.getId(), event.getCourseId(),
                    course != null ? course.getCode() : null,
                    course != null ? course.getTitle() : null,
                    term != null ? term.label() : null,
                    event.getStudentId(),
                    student != null ? student.getFirstName() + " " + student.getLastName() : "Bilinmiyor",
                    student != null ? student.getStudentNumber() : null,
                    event.getType(), event.getActorId(), event.getReason(), event.getOccurredAt(), late);
        }).toList();
    }

    private Course find(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));
    }

    private StudentCourseEnrollment activeEnrollment(UUID courseId, UUID studentId, String message) {
        return enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .filter(StudentCourseEnrollment::isActive)
                .orElseThrow(() -> new EnrollmentNotFoundException(message));
    }

    private void evict(Course course, UUID studentId) {
        courseCaches.evictStudentCourses(studentId);
        courseCaches.evictStaffCourses(course);
    }

    private static String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }
}
