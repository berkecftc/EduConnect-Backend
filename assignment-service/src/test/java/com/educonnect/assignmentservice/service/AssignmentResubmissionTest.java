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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void resubmission_shouldClearPreviousGradeAndFeedback() {
        UUID assignmentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Assignment assignment = new Assignment();
        assignment.setDueDate(LocalDateTime.now().plusDays(1));
        AssignmentSubmission graded = new AssignmentSubmission(assignmentId, studentId, "old", false);
        graded.setGrade(85);
        graded.setFeedback("İyi iş");
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.of(graded));
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentSubmission result = assignmentService.submitAssignment(assignmentId, studentId, null);

        assertThat(result.getGrade()).isNull();
        assertThat(result.getFeedback()).isNull();
    }
}
