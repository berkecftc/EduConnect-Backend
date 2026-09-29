package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseClient;
import com.educonnect.assignmentservice.client.CourseInternalClient;
import com.educonnect.assignmentservice.client.UserClient;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.publisher.AssignmentProducer;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
class AssignmentResubmissionTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private MinioService minioService;
    @Mock
    private CourseClient courseClient;
    @Mock
    private CourseInternalClient courseInternalClient;
    @Mock
    private UserClient userClient;
    @Mock
    private AssignmentProducer assignmentProducer;

    @InjectMocks
    private AssignmentService assignmentService;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @Test
    void gradedSubmissionCannotBeReplaced() {
        givenAssignmentDue(LocalDateTime.now().plusDays(1));
        AssignmentSubmission graded = new AssignmentSubmission(assignmentId, studentId, "old", false);
        graded.setGrade(85);
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.of(graded));

        assertThatThrownBy(() -> assignmentService.submitAssignment(assignmentId, studentId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(graded.getGrade()).isEqualTo(85);
        verify(submissionRepository, never()).save(any());
        verify(minioService, never()).uploadFile(any());
    }

    @Test
    void submissionCannotBeReplacedAfterDeadline() {
        givenAssignmentDue(LocalDateTime.now().minusHours(1));
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId))
                .thenReturn(Optional.of(new AssignmentSubmission(assignmentId, studentId, "old", false)));

        assertThatThrownBy(() -> assignmentService.submitAssignment(assignmentId, studentId, null))
                .isInstanceOf(ResponseStatusException.class);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void ungradedSubmissionCanBeReplacedBeforeDeadline() {
        givenAssignmentDue(LocalDateTime.now().plusDays(1));
        AssignmentSubmission previous = new AssignmentSubmission(assignmentId, studentId, "old", false);
        previous.setFeedback("Dosya eksik");
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.of(previous));
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentSubmission result = assignmentService.submitAssignment(assignmentId, studentId, null);

        assertThat(result.getFeedback()).isNull();
        assertThat(result.isLate()).isFalse();
    }

    @Test
    void firstSubmissionAfterDeadlineIsAcceptedAsLate() {
        givenAssignmentDue(LocalDateTime.now().minusHours(1));
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(assignmentService.submitAssignment(assignmentId, studentId, null).isLate()).isTrue();
    }

    @Test
    void assignmentWithoutDueDateDoesNotFail() {
        givenAssignmentDue(null);
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(assignmentService.submitAssignment(assignmentId, studentId, null).isLate()).isFalse();
    }

    private void givenAssignmentDue(LocalDateTime dueDate) {
        Assignment assignment = new Assignment();
        assignment.setDueDate(dueDate);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    }
}
