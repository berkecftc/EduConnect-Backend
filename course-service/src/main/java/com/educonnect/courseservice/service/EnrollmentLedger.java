package com.educonnect.courseservice.service;

import com.educonnect.courseservice.exception.AlreadyEnrolledException;
import com.educonnect.courseservice.model.CourseEnrollmentEvent;
import com.educonnect.courseservice.model.EnrollmentEventType;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.repository.CourseEnrollmentEventRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Transactional
public class EnrollmentLedger {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseEnrollmentEventRepository eventRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public EnrollmentLedger(EnrollmentRepository enrollmentRepository, CourseEnrollmentEventRepository eventRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.eventRepository = eventRepository;
    }

    public StudentCourseEnrollment enroll(UUID courseId, UUID studentId, UUID actorId) {
        StudentCourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElse(null);
        if (enrollment == null) {
            enrollment = new StudentCourseEnrollment(courseId, studentId);
        } else if (enrollment.isActive()) {
            throw new AlreadyEnrolledException("Öğrenci bu derse zaten kayıtlı.");
        } else {
            enrollment.reactivate(LocalDateTime.now(clock));
        }
        StudentCourseEnrollment saved = enrollmentRepository.save(enrollment);
        record(courseId, studentId, EnrollmentEventType.ENROLLED, actorId, null);
        return saved;
    }

    public CourseEnrollmentEvent end(StudentCourseEnrollment enrollment, EnrollmentEventType type, UUID actorId,
                                     String reason) {
        Instant now = Instant.now(clock);
        enrollment.withdraw(actorId, reason, now);
        enrollmentRepository.save(enrollment);
        return record(enrollment.getCourseId(), enrollment.getStudentId(), type, actorId, reason);
    }

    private CourseEnrollmentEvent record(UUID courseId, UUID studentId, EnrollmentEventType type, UUID actorId,
                                         String reason) {
        return eventRepository.save(new CourseEnrollmentEvent(courseId, studentId, type, actorId, reason, Instant.now(clock)));
    }
}
