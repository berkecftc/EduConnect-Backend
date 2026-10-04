package com.educonnect.eventservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.TurkishDates;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventReminderService {

    private static final Logger log = LoggerFactory.getLogger(EventReminderService.class);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventNotifier notifier;
    private final EventSchedule schedule;
    private final JdbcTemplate jdbcTemplate;
    private final Duration lead;

    public EventReminderService(EventRepository eventRepository,
                                EventRegistrationRepository registrationRepository,
                                EventNotifier notifier,
                                EventSchedule schedule,
                                JdbcTemplate jdbcTemplate,
                                @Value("${educonnect.event.reminder-lead:PT24H}") Duration lead) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.notifier = notifier;
        this.schedule = schedule;
        this.jdbcTemplate = jdbcTemplate;
        this.lead = lead;
    }

    @Scheduled(cron = "${educonnect.event.reminder-cron:0 */15 * * * *}")
    public void remindScheduled() {
        remind(schedule.now());
    }

    @Transactional
    public int remind(LocalDateTime now) {
        int sent = 0;
        for (Event event : eventRepository.findByStatusAndStartsAtAfterAndStartsAtLessThanEqual(EventStatus.ACTIVE, now, now.plus(lead))) {
            String key = "event:" + event.getId() + ":" + event.getStartsAt() + ":";
            List<UUID> due = registrationRepository.findByEventId(event.getId()).stream()
                    .filter(registration -> registration.getStatus() == RegistrationStatus.REGISTERED)
                    .map(EventRegistration::getStudentId)
                    .filter(student -> claim(key + student))
                    .toList();
            if (due.isEmpty()) {
                continue;
            }
            notifier.notify(due, NotificationCategory.EVENT, "EVENT_REMINDER", event,
                    "Yaklaşan etkinlik: " + event.getTitle(),
                    "Kayıtlı olduğunuz \"" + event.getTitle() + "\" etkinliği " + TurkishDates.format(event.getStartsAt())
                            + " tarihinde başlıyor.\nYer: " + (event.getLocation() == null ? "-" : event.getLocation())
                            + "\nGirişte bilet QR kodunuzu görevliye gösterin. Katılamayacaksanız kaydınızı iptal edebilirsiniz.");
            sent++;
        }
        jdbcTemplate.update("delete from sent_reminders where sent_at < now() - interval '60 days'");
        if (sent > 0) {
            log.info("Event reminders sent: events={}", sent);
        }
        return sent;
    }

    private boolean claim(String key) {
        return jdbcTemplate.update("insert into sent_reminders (reminder_key) values (?) on conflict do nothing", key) == 1;
    }
}
