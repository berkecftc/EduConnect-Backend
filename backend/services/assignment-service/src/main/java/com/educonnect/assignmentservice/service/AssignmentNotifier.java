package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseClient;
import com.educonnect.assignmentservice.client.CourseInternalClient;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class AssignmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(AssignmentNotifier.class);

    private final OutboxPublisher outboxPublisher;
    private final CourseClient courseClient;
    private final CourseInternalClient courseInternalClient;
    private final SubmissionRepository submissionRepository;
    private final GroupWork groupWork;

    public AssignmentNotifier(OutboxPublisher outboxPublisher, CourseClient courseClient,
                              CourseInternalClient courseInternalClient, SubmissionRepository submissionRepository,
                              GroupWork groupWork) {
        this.outboxPublisher = outboxPublisher;
        this.courseClient = courseClient;
        this.courseInternalClient = courseInternalClient;
        this.submissionRepository = submissionRepository;
        this.groupWork = groupWork;
    }

    public void notify(Collection<UUID> recipientIds, Assignment assignment, String type, String subject, String body) {
        if (recipientIds == null) {
            return;
        }
        List<UUID> recipients = recipientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            return;
        }
        outboxPublisher.publish(NotificationRequest.EXCHANGE, NotificationRequest.ROUTING_KEY,
                NotificationRequest.of(recipients, NotificationCategory.COURSE, type,
                        "[" + courseCode(assignment.getCourseId()) + "] " + subject, body,
                        "/courses/" + assignment.getCourseId() + "/assignments/" + assignment.getId(), null));
    }

    public void notifyEnrolled(Assignment assignment, String type, String subject, String body) {
        List<UUID> enrolled;
        try {
            enrolled = courseInternalClient.getEnrolledStudentIds(assignment.getCourseId());
        } catch (RuntimeException e) {
            log.warn("Enrolled students could not be resolved for notification: course={}, error={}",
                    assignment.getCourseId(), e.getMessage());
            return;
        }
        notify(enrolled, assignment, type, subject, body);
    }

    public List<UUID> submitters(Assignment assignment) {
        List<AssignmentSubmission> submissions = submissionRepository.findByAssignmentId(assignment.getId());
        return studentsOf(submissions);
    }

    public List<UUID> studentsOf(Collection<AssignmentSubmission> submissions) {
        List<UUID> students = new ArrayList<>();
        List<UUID> groupIds = new ArrayList<>();
        for (AssignmentSubmission submission : submissions) {
            if (submission.getGroupId() != null) {
                groupIds.add(submission.getGroupId());
            } else {
                students.add(submission.getStudentId());
            }
        }
        groupWork.membersByGroup(groupIds).values().forEach(students::addAll);
        return students;
    }

    private String courseCode(UUID courseId) {
        try {
            Map<String, Object> course = courseClient.getCourseById(courseId);
            Object code = course != null ? course.get("code") : null;
            if (code != null) {
                return code.toString();
            }
        } catch (RuntimeException e) {
            log.warn("Course code could not be resolved for notification: course={}, error={}", courseId, e.getMessage());
        }
        return "Ders";
    }
}
