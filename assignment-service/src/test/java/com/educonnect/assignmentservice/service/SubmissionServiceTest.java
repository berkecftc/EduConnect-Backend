package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentExtension;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.model.SubmissionVersion;
import com.educonnect.assignmentservice.repository.AssignmentExtensionRepository;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.GroupMemberRepository;
import com.educonnect.assignmentservice.repository.MemberGradeRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.assignmentservice.repository.SubmissionVersionRepository;
import com.educonnect.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionVersionRepository versionRepository;
    @Mock
    private AssignmentExtensionRepository extensionRepository;
    @Mock
    private MinioService minioService;
    @Mock
    private GroupMemberRepository memberRepository;
    @Mock
    private MemberGradeRepository memberGradeRepository;
    @Mock
    private StudentAssignmentCache studentCache;

    private SubmissionService service;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private Assignment assignment;

    @BeforeEach
    void setUp() {
        service = new SubmissionService(assignmentRepository, submissionRepository, versionRepository,
                new GroupWork(memberRepository, submissionRepository, extensionRepository, memberGradeRepository),
                minioService, studentCache, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        assignment = new Assignment();
        assignment.setId(assignmentId);
        lenient().when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    }

    @Test
    void anEmptySubmissionIsRejected() {
        assertCode(() -> service.submit(assignmentId, studentId, null, "  ", null, null), "SUBMISSION_EMPTY");
    }

    @Test
    void aGradedSubmissionCannotBeReplaced() {
        assignment.setDueDate(NOW.plusDays(1));
        AssignmentSubmission graded = new AssignmentSubmission(assignmentId, studentId, "old", false);
        graded.setGrade(BigDecimal.valueOf(85));
        noExtension();
        when(submissionRepository.findByAssignmentIdAndStudentIdAndGroupIdIsNull(assignmentId, studentId)).thenReturn(Optional.of(graded));

        assertCode(() -> service.submit(assignmentId, studentId, null, "yeni", null, null), "SUBMISSION_GRADED");
        assertThat(graded.getGrade()).isEqualByComparingTo("85");
        verify(minioService, never()).uploadFile(any());
    }

    @Test
    void anUngradedSubmissionIsReplacedBeforeTheDeadlineAndKeptAsANewVersion() {
        assignment.setDueDate(NOW.plusDays(1));
        AssignmentSubmission previous = new AssignmentSubmission(assignmentId, studentId, "old", false);
        noExtension();
        when(submissionRepository.findByAssignmentIdAndStudentIdAndGroupIdIsNull(assignmentId, studentId)).thenReturn(Optional.of(previous));
        whenSaved();
        when(versionRepository.countBySubmissionId(any())).thenReturn(1);

        AssignmentSubmission result = service.submit(assignmentId, studentId, null, " Cevabım ", null, null);

        assertThat(result.getTextContent()).isEqualTo("Cevabım");
        assertThat(result.getSubmissionFileUrl()).isNull();
        assertThat(result.isLate()).isFalse();
        ArgumentCaptor<SubmissionVersion> version = ArgumentCaptor.forClass(SubmissionVersion.class);
        verify(versionRepository).save(version.capture());
        assertThat(version.getValue().getVersionNo()).isEqualTo(2);
        assertThat(version.getValue().getTextContent()).isEqualTo("Cevabım");
    }

    @Test
    void withoutALateWindowSubmissionsCloseAtTheDeadline() {
        assignment.setDueDate(NOW.minusHours(1));
        noExtension();

        assertCode(() -> service.submit(assignmentId, studentId, null, "geç", null, null), "SUBMISSION_CLOSED");
    }

    @Test
    void theLateWindowAcceptsFirstSubmissionsAsLateButNotReplacements() {
        assignment.setDueDate(NOW.minusHours(1));
        assignment.setLateUntil(NOW.plusDays(1));
        noExtension();
        when(submissionRepository.findByAssignmentIdAndStudentIdAndGroupIdIsNull(assignmentId, studentId)).thenReturn(Optional.empty());
        whenSaved();

        assertThat(service.submit(assignmentId, studentId, null, "geç", null, null).isLate()).isTrue();

        when(submissionRepository.findByAssignmentIdAndStudentIdAndGroupIdIsNull(assignmentId, studentId))
                .thenReturn(Optional.of(new AssignmentSubmission(assignmentId, studentId, null, true)));
        assertCode(() -> service.submit(assignmentId, studentId, null, "tekrar", null, null), "RESUBMISSION_CLOSED");
    }

    @Test
    void submissionsCloseWhenTheLateWindowEnds() {
        assignment.setDueDate(NOW.minusDays(2));
        assignment.setLateUntil(NOW.minusDays(1));
        noExtension();

        assertCode(() -> service.submit(assignmentId, studentId, null, "çok geç", null, null), "SUBMISSION_CLOSED");
    }

    @Test
    void anExtensionMovesTheStudentsDeadline() {
        assignment.setDueDate(NOW.minusHours(1));
        AssignmentExtension extension = new AssignmentExtension(assignmentId, studentId);
        extension.grant(NOW.plusDays(2), "Rapor", UUID.randomUUID(), Instant.now());
        when(extensionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.of(extension));
        when(submissionRepository.findByAssignmentIdAndStudentIdAndGroupIdIsNull(assignmentId, studentId)).thenReturn(Optional.empty());
        whenSaved();

        assertThat(service.submit(assignmentId, studentId, null, "raporlu", null, null).isLate()).isFalse();
    }

    @Test
    void anAssignmentWithoutADueDateStaysOpen() {
        noExtension();
        when(submissionRepository.findByAssignmentIdAndStudentIdAndGroupIdIsNull(assignmentId, studentId)).thenReturn(Optional.empty());
        whenSaved();

        assertThat(service.submit(assignmentId, studentId, null, "cevap", null, null).isLate()).isFalse();
    }

    @Test
    void latePenaltyAppliesOnlyToLateGrades() {
        assignment.setLatePenaltyPercent(BigDecimal.TEN);

        assertThat(DeadlinePolicy.finalGrade(assignment, BigDecimal.valueOf(80), true)).isEqualByComparingTo("72");
        assertThat(DeadlinePolicy.finalGrade(assignment, BigDecimal.valueOf(80), false)).isEqualByComparingTo("80");
        assertThat(DeadlinePolicy.finalGrade(assignment, null, true)).isNull();
    }

    private void noExtension() {
        when(extensionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.empty());
    }

    private void whenSaved() {
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }
}
