package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.AssignmentServiceClient;
import com.educonnect.llmservice.client.AssignmentServiceClient.AssignmentResponse;
import com.educonnect.llmservice.client.AssignmentServiceClient.SubmissionResponse;
import com.educonnect.llmservice.client.CourseServiceClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingAssignmentsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);
    private static final String STUDENT = "5eed0000-0000-4000-8000-00000000b001";

    private final AssignmentServiceClient assignmentClient = mock(AssignmentServiceClient.class);
    private final CourseServiceClient courseClient = mock(CourseServiceClient.class);
    private final PendingAssignments pending = new PendingAssignments(assignmentClient, courseClient,
            Clock.fixed(NOW.atZone(ZoneId.of("Europe/Istanbul")).toInstant(), ZoneId.of("Europe/Istanbul")));

    @Test
    void openAssignmentsIncludeLateWindowsWithCourseNamesAndTheInstructorsAiPolicy() {
        when(courseClient.getMyCourses(STUDENT)).thenReturn(List.of(new CourseServiceClient.EnrolledCourse("c1", "Veri Yapıları", "BIL201")));
        when(assignmentClient.getMyAssignments(STUDENT)).thenReturn(List.of(
                assignment("Bağlı liste", "c1", NOW.plusDays(2), null, "HOMEWORK", null, null),
                assignment("Geç kabul", "c1", NOW.minusHours(3), NOW.plusDays(1), "PROJECT", "NONE", new BigDecimal("10.00")),
                assignment("Kapanmış", "c1", NOW.minusDays(1), null, "HOMEWORK", "GUIDANCE", null),
                assignment("Teslim edilmiş", "c1", NOW.plusDays(1), null, "HOMEWORK", "GUIDANCE", null, submission()),
                assignment("Beyanlı", "c9", NOW.plusDays(5), null, "LAB", "ALLOWED_WITH_DISCLOSURE", null)));

        List<PendingAssignments.PendingAssignment> result = pending.of(STUDENT);

        assertThat(result).extracting(PendingAssignments.PendingAssignment::title).containsExactly("Geç kabul", "Bağlı liste", "Beyanlı");
        PendingAssignments.PendingAssignment late = result.get(0);
        assertThat(late.courseCode()).isEqualTo("BIL201");
        assertThat(late.courseTitle()).isEqualTo("Veri Yapıları");
        assertThat(late.type()).isEqualTo("Proje");
        assertThat(late.status()).contains("geç teslim", "11 Ekim 2026 12:00", "%10 kesinti");
        assertThat(late.aiPolicy()).isEqualTo("NONE");
        assertThat(late.aiHelp()).contains("NOT allowed");
        PendingAssignments.PendingAssignment normal = result.get(1);
        assertThat(normal.status()).isEqualTo("Teslim edilmedi");
        assertThat(normal.dueDate()).isEqualTo("12 Ekim 2026 12:00");
        assertThat(normal.aiPolicy()).isEqualTo("GUIDANCE");
        assertThat(normal.aiHelp()).contains("Never write the answer");
        PendingAssignments.PendingAssignment disclosed = result.get(2);
        assertThat(disclosed.courseCode()).isNull();
        assertThat(disclosed.aiHelp()).contains("declare");
    }

    @Test
    void allAssignmentsKeepSubmittedAndClosedOnesForPolicyChecks() {
        when(courseClient.getMyCourses(STUDENT)).thenReturn(List.of());
        when(assignmentClient.getMyAssignments(STUDENT)).thenReturn(List.of(
                assignment("Kapanmış", "c1", NOW.minusDays(1), null, "HOMEWORK", "NONE", null),
                assignment("Teslim edilmiş", "c1", NOW.plusDays(1), null, "HOMEWORK", "NONE", null, submission())));

        assertThat(pending.all(STUDENT)).extracting(PendingAssignments.PendingAssignment::status)
                .containsExactly("Teslim süresi doldu", "Teslim edildi");
        assertThat(pending.of(STUDENT)).isEmpty();
    }

    @Test
    void aMissingCourseListStillReturnsTheAssignments() {
        when(courseClient.getMyCourses(STUDENT)).thenThrow(new IllegalStateException("course-service down"));
        when(assignmentClient.getMyAssignments(STUDENT)).thenReturn(List.of(
                assignment("Bağlı liste", "c1", NOW.plusDays(2), null, "QUIZ", "GUIDANCE", null)));

        assertThat(pending.of(STUDENT)).singleElement()
                .satisfies(item -> {
                    assertThat(item.courseTitle()).isNull();
                    assertThat(item.type()).isEqualTo("Kısa sınav");
                });
    }

    private static AssignmentResponse assignment(String title, String courseId, LocalDateTime due, LocalDateTime lateUntil,
                                                 String type, String aiPolicy, BigDecimal penalty) {
        return assignment(title, courseId, due, lateUntil, type, aiPolicy, penalty, null);
    }

    private static AssignmentResponse assignment(String title, String courseId, LocalDateTime due, LocalDateTime lateUntil,
                                                 String type, String aiPolicy, BigDecimal penalty, SubmissionResponse submission) {
        return new AssignmentResponse("a-" + title, title, null, due.toString(), courseId, null, type, aiPolicy, penalty,
                due.toString(), lateUntil != null ? lateUntil.toString() : null, submission);
    }

    private static SubmissionResponse submission() {
        return new SubmissionResponse("s1", NOW.minusHours(1).toString(), null, null, false);
    }
}
