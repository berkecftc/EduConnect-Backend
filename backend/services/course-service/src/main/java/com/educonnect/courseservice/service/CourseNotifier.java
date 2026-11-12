package com.educonnect.courseservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseStaff;
import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.repository.CourseStaffRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class CourseNotifier {

    private final OutboxPublisher outboxPublisher;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseStaffRepository staffRepository;

    public CourseNotifier(OutboxPublisher outboxPublisher, EnrollmentRepository enrollmentRepository,
                          CourseStaffRepository staffRepository) {
        this.outboxPublisher = outboxPublisher;
        this.enrollmentRepository = enrollmentRepository;
        this.staffRepository = staffRepository;
    }

    public void notify(Collection<UUID> recipientIds, String type, Course course, String subject, String body) {
        if (recipientIds == null) {
            return;
        }
        List<UUID> recipients = recipientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            return;
        }
        outboxPublisher.publish(NotificationRequest.EXCHANGE, NotificationRequest.ROUTING_KEY,
                NotificationRequest.of(recipients, NotificationCategory.COURSE, type, "[" + course.getCode() + "] " + subject,
                        body, "/courses/" + course.getId(), null));
    }

    public void notifyStudents(Course course, String type, String subject, String body) {
        notify(enrollmentRepository.findByCourseIdAndIsActive(course.getId(), true).stream()
                .map(StudentCourseEnrollment::getStudentId)
                .toList(), type, course, subject, body);
    }

    public void notifyTeachers(Course course, String type, String subject, String body) {
        List<UUID> teachers = new ArrayList<>();
        teachers.add(course.getInstructorId());
        staffRepository.findByCourseIdOrderByCreatedAtAsc(course.getId()).stream()
                .filter(member -> member.getRole().teaches())
                .map(CourseStaff::getUserId)
                .forEach(teachers::add);
        notify(teachers, type, course, subject, body);
    }

    static String roleName(CourseStaffRole role) {
        return switch (role) {
            case COORDINATOR -> "koordinatör";
            case INSTRUCTOR -> "öğretim elemanı";
            case ASSISTANT -> "asistan";
        };
    }

    static String withReason(String text, String reason) {
        return reason == null || reason.isBlank() ? text : text + "\nGerekçe: " + reason.strip();
    }
}
