package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentExtension;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentExtensionRepository;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.assignmentservice.service.AssignmentReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AssignmentIntegrationTest
class AssignmentReminderTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID submitted = UUID.randomUUID();
    private final UUID pending = UUID.randomUUID();
    private final UUID extended = UUID.randomUUID();

    @Autowired
    private AssignmentReminderService reminderService;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AssignmentExtensionRepository extensionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyStudentsWhoHaveNotSubmittedAreRemindedAndExtensionsGetTheirOwnReminder() {
        FakeCourseService.course(courseId, instructor, submitted, pending, extended);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Assignment dueTomorrow = assignment(now.plusHours(20));
        Assignment dueLater = assignment(now.plusDays(5));
        submissionRepository.save(new AssignmentSubmission(dueTomorrow.getId(), submitted, null, false));
        AssignmentExtension extension = new AssignmentExtension(dueTomorrow.getId(), extended);
        extension.grant(now.plusDays(3), "Rapor", instructor, Instant.now());
        extensionRepository.save(extension);

        reminderService.remind(now);
        reminderService.remind(now.plusMinutes(15));

        assertThat(reminders(dueTomorrow)).singleElement().asString()
                .contains(pending.toString(), "\"category\":\"COURSE\"", "Teslim yaklaşıyor", "Henüz teslim etmediniz")
                .doesNotContain(submitted.toString(), extended.toString());
        assertThat(reminders(dueLater)).isEmpty();

        reminderService.remind(now.plusDays(2).plusHours(1));
        assertThat(reminders(dueTomorrow)).hasSize(2)
                .anySatisfy(body -> assertThat(body).contains(extended.toString()).doesNotContain(pending.toString()));
    }

    private List<String> reminders(Assignment assignment) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from assignment_db.outbox_messages "
                        + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ? "
                        + "and convert_from(body, 'UTF8') like '%\"type\":\"ASSIGNMENT_REMINDER\"%'",
                String.class, "%" + assignment.getId() + "%");
    }

    private Assignment assignment(LocalDateTime due) {
        Assignment assignment = new Assignment();
        assignment.setTitle("Hatırlatmalı Ödev " + UUID.randomUUID());
        assignment.setCourseId(courseId);
        assignment.setDueDate(due);
        return assignmentRepository.save(assignment);
    }
}
