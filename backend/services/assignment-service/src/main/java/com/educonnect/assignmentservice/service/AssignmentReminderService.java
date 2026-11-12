package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseInternalClient;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentExtension;
import com.educonnect.assignmentservice.repository.AssignmentExtensionRepository;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.common.messaging.notification.TurkishDates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssignmentReminderService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentReminderService.class);
    static final String TYPE = "ASSIGNMENT_REMINDER";

    private final AssignmentRepository assignmentRepository;
    private final AssignmentExtensionRepository extensionRepository;
    private final CourseInternalClient courseInternalClient;
    private final AssignmentNotifier notifier;
    private final GroupWork groupWork;
    private final JdbcTemplate jdbcTemplate;
    private final Duration lead;
    private final Clock clock = Clock.systemDefaultZone();

    public AssignmentReminderService(AssignmentRepository assignmentRepository,
                                     AssignmentExtensionRepository extensionRepository,
                                     CourseInternalClient courseInternalClient,
                                     AssignmentNotifier notifier,
                                     GroupWork groupWork,
                                     JdbcTemplate jdbcTemplate,
                                     @Value("${educonnect.assignment.reminder-lead:PT24H}") Duration lead) {
        this.assignmentRepository = assignmentRepository;
        this.extensionRepository = extensionRepository;
        this.courseInternalClient = courseInternalClient;
        this.notifier = notifier;
        this.groupWork = groupWork;
        this.jdbcTemplate = jdbcTemplate;
        this.lead = lead;
    }

    @Scheduled(cron = "${educonnect.assignment.reminder-cron:0 */15 * * * *}")
    public void remindScheduled() {
        remind(LocalDateTime.now(clock));
    }

    @Transactional
    public int remind(LocalDateTime now) {
        LocalDateTime until = now.plus(lead);
        int sent = 0;
        for (Assignment assignment : assignmentRepository.findByDueDateAfterAndDueDateLessThanEqual(now, until)) {
            if (remindClass(assignment)) {
                sent++;
            }
        }
        for (AssignmentExtension extension : extensionRepository.findByDueDateAfterAndDueDateLessThanEqual(now, until)) {
            if (remindExtended(extension)) {
                sent++;
            }
        }
        jdbcTemplate.update("delete from sent_reminders where sent_at < now() - interval '60 days'");
        if (sent > 0) {
            log.info("Assignment reminders sent: {}", sent);
        }
        return sent;
    }

    private boolean remindClass(Assignment assignment) {
        List<UUID> enrolled;
        try {
            enrolled = courseInternalClient.getEnrolledStudentIds(assignment.getCourseId());
        } catch (RuntimeException e) {
            log.warn("Reminder postponed, enrolled students unavailable: assignment={}, error={}", assignment.getId(), e.getMessage());
            return false;
        }
        Set<UUID> excluded = new HashSet<>(notifier.submitters(assignment));
        extensionRepository.findByAssignmentIdOrderByDueDateAsc(assignment.getId()).stream()
                .filter(extension -> extension.getDueDate().isAfter(assignment.getDueDate()))
                .map(AssignmentExtension::getStudentId)
                .forEach(excluded::add);
        String key = "assignment:" + assignment.getId() + ":" + assignment.getDueDate() + ":";
        List<UUID> pending = enrolled == null ? List.of() : enrolled.stream()
                .filter(id -> !excluded.contains(id))
                .filter(id -> claim(key + id))
                .toList();
        if (pending.isEmpty()) {
            return false;
        }
        notify(pending, assignment, assignment.getDueDate());
        return true;
    }

    private boolean remindExtended(AssignmentExtension extension) {
        Assignment assignment = assignmentRepository.findById(extension.getAssignmentId()).orElse(null);
        if (assignment == null || groupWork.submissionOf(assignment, extension.getStudentId()).isPresent()
                || !claim("extension:" + extension.getId() + ":" + extension.getDueDate())) {
            return false;
        }
        notify(List.of(extension.getStudentId()), assignment, extension.getDueDate());
        return true;
    }

    private void notify(List<UUID> students, Assignment assignment, LocalDateTime due) {
        notifier.notify(students, assignment, TYPE, "Teslim yaklaşıyor: " + assignment.getTitle(),
                "\"" + assignment.getTitle() + "\" için son teslim " + TurkishDates.format(due)
                        + ". Henüz teslim etmediniz.");
    }

    private boolean claim(String key) {
        return jdbcTemplate.update("insert into sent_reminders (reminder_key) values (?) on conflict do nothing", key) == 1;
    }
}
