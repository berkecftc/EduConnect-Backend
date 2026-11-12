package com.educonnect.llmservice.service;

import com.educonnect.common.messaging.notification.TurkishDates;
import com.educonnect.llmservice.client.AssignmentServiceClient;
import com.educonnect.llmservice.client.CourseServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PendingAssignments {

    private static final Logger log = LoggerFactory.getLogger(PendingAssignments.class);

    public record PendingAssignment(String courseCode, String courseTitle, String title, String type, String dueDate,
                                    String status, String aiPolicy, String aiHelp) {
    }

    private static final Map<String, String> TYPES = Map.of(
            "HOMEWORK", "Ödev", "PROJECT", "Proje", "QUIZ", "Kısa sınav", "LAB", "Laboratuvar",
            "MIDTERM", "Ara sınav", "FINAL", "Final", "OTHER", "Diğer");

    private final AssignmentServiceClient assignmentClient;
    private final CourseServiceClient courseClient;
    private final Clock clock;

    @Autowired
    public PendingAssignments(AssignmentServiceClient assignmentClient, CourseServiceClient courseClient) {
        this(assignmentClient, courseClient, Clock.systemDefaultZone());
    }

    PendingAssignments(AssignmentServiceClient assignmentClient, CourseServiceClient courseClient, Clock clock) {
        this.assignmentClient = assignmentClient;
        this.courseClient = courseClient;
        this.clock = clock;
    }

    public List<PendingAssignment> of(String studentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, CourseServiceClient.EnrolledCourse> courses = courses(studentId);
        return assignmentClient.getMyAssignments(studentId).stream()
                .filter(assignment -> !submitted(assignment))
                .filter(assignment -> isOpen(assignment, now))
                .sorted(Comparator.comparing(assignment -> Objects.requireNonNullElse(due(assignment), LocalDateTime.MAX)))
                .map(assignment -> toPending(assignment, courses.get(assignment.courseId()), now))
                .toList();
    }

    public List<PendingAssignment> all(String studentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, CourseServiceClient.EnrolledCourse> courses = courses(studentId);
        return assignmentClient.getMyAssignments(studentId).stream()
                .map(assignment -> toPending(assignment, courses.get(assignment.courseId()), now))
                .toList();
    }

    private static boolean submitted(AssignmentServiceClient.AssignmentResponse assignment) {
        return assignment.submission() != null && assignment.submission().submissionId() != null;
    }

    public static String aiHelp(String policy) {
        if ("NONE".equals(policy)) {
            return "AI help is NOT allowed for this assignment. Do not help with its content at all; only share deadline and "
                    + "status information and suggest course materials or the instructor's office hours.";
        }
        if ("ALLOWED_WITH_DISCLOSURE".equals(policy)) {
            return "AI help is allowed. You may help fully, but remind the student that they must declare their AI use "
                    + "when submitting.";
        }
        return "Guidance only: explain concepts, give hints and ask guiding questions, show the method on a different "
                + "example. Never write the answer, solution, code or text that would be submitted.";
    }

    private PendingAssignment toPending(AssignmentServiceClient.AssignmentResponse assignment,
                                        CourseServiceClient.EnrolledCourse course, LocalDateTime now) {
        LocalDateTime due = due(assignment);
        String status;
        if (submitted(assignment)) {
            status = "Teslim edildi";
        } else if (!isOpen(assignment, now)) {
            status = "Teslim süresi doldu";
        } else if (due != null && now.isAfter(due)) {
            BigDecimal penalty = Objects.requireNonNullElse(assignment.latePenaltyPercent(), BigDecimal.ZERO);
            status = "Son tarih geçti; geç teslim " + TurkishDates.format(lateUntil(assignment)) + " tarihine kadar açık"
                    + (penalty.signum() > 0 ? " (%" + penalty.stripTrailingZeros().toPlainString() + " kesinti)" : "");
        } else {
            status = "Teslim edilmedi";
        }
        String policy = assignment.aiPolicy() == null ? "GUIDANCE" : assignment.aiPolicy();
        return new PendingAssignment(
                course != null ? course.code() : null,
                course != null ? course.title() : null,
                assignment.title(),
                TYPES.getOrDefault(assignment.type(), "Ödev"),
                due != null ? TurkishDates.format(due) : "Belirtilmedi",
                status,
                policy,
                aiHelp(policy));
    }

    private Map<String, CourseServiceClient.EnrolledCourse> courses(String studentId) {
        try {
            return courseClient.getMyCourses(studentId).stream()
                    .collect(Collectors.toMap(CourseServiceClient.EnrolledCourse::id, Function.identity(), (first, second) -> first));
        } catch (RuntimeException e) {
            log.warn("Courses could not be loaded for the assistant: {}", e.getMessage());
            return Map.of();
        }
    }

    private static boolean isOpen(AssignmentServiceClient.AssignmentResponse assignment, LocalDateTime now) {
        LocalDateTime closes = lateUntil(assignment);
        if (closes == null) {
            closes = due(assignment);
        }
        return closes == null || !now.isAfter(closes);
    }

    private static LocalDateTime due(AssignmentServiceClient.AssignmentResponse assignment) {
        LocalDateTime effective = parse(assignment.effectiveDueDate());
        return effective != null ? effective : parse(assignment.dueDate());
    }

    private static LocalDateTime lateUntil(AssignmentServiceClient.AssignmentResponse assignment) {
        return parse(assignment.effectiveLateUntil());
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
