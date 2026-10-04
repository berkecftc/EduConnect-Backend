package com.educonnect.eventservice;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.service.EventReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EventIntegrationTest
class EventReminderTest {

    private final UUID attendee = UUID.randomUUID();
    private final UUID quitter = UUID.randomUUID();
    private final UUID latecomer = UUID.randomUUID();

    @Autowired
    private EventReminderService reminderService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registeredParticipantsAreRemindedOnceADayBeforeAndAgainAfterAPostponement() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Event tomorrow = event(now.plusHours(20));
        Event nextWeek = event(now.plusDays(7));
        register(tomorrow, attendee, RegistrationStatus.REGISTERED);
        register(tomorrow, quitter, RegistrationStatus.CANCELLED);
        register(nextWeek, attendee, RegistrationStatus.REGISTERED);

        reminderService.remind(now);
        reminderService.remind(now.plusMinutes(15));

        assertThat(reminders(tomorrow)).singleElement().asString()
                .contains(attendee.toString(), "\"category\":\"EVENT\"", "Yaklaşan etkinlik: " + tomorrow.getTitle(), "B-101")
                .doesNotContain(quitter.toString());
        assertThat(reminders(nextWeek)).isEmpty();

        register(tomorrow, latecomer, RegistrationStatus.REGISTERED);
        reminderService.remind(now.plusMinutes(20));
        assertThat(reminders(tomorrow)).hasSize(2)
                .anySatisfy(body -> assertThat(body).contains(latecomer.toString()).doesNotContain(attendee.toString()));

        tomorrow.setStartsAt(now.plusHours(23));
        tomorrow.setEndsAt(now.plusHours(25));
        eventRepository.save(tomorrow);
        reminderService.remind(now.plusMinutes(30));
        assertThat(reminders(tomorrow)).hasSize(3)
                .anySatisfy(body -> assertThat(body).contains(attendee.toString(), latecomer.toString()));
    }

    private List<String> reminders(Event event) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from event_db.outbox_messages "
                        + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ? "
                        + "and convert_from(body, 'UTF8') like '%\"type\":\"EVENT_REMINDER\"%'",
                String.class, "%" + event.getId() + "%");
    }

    private void register(Event event, UUID student, RegistrationStatus status) {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(event.getId());
        registration.setStudentId(student);
        registration.setQrCode(UUID.randomUUID().toString());
        registration.setStatus(status);
        registrationRepository.save(registration);
    }

    private Event event(LocalDateTime startsAt) {
        Event event = new Event();
        event.setTitle("Hatırlatmalı " + UUID.randomUUID());
        event.setStartsAt(startsAt);
        event.setEndsAt(startsAt.plusHours(2));
        event.setLocation("B-101");
        event.setClubId(UUID.randomUUID());
        event.setClubName("Kulüp");
        event.setStatus(EventStatus.ACTIVE);
        event.setPublishedAt(LocalDateTime.now());
        return eventRepository.save(event);
    }
}
