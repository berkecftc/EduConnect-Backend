package com.educonnect.eventservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.eventservice.model.Event;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventScheduleTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 12, 0);

    private final EventSchedule schedule = new EventSchedule(Duration.ofHours(24), Duration.ofMinutes(60), Duration.ofHours(2),
            Clock.fixed(NOW.atZone(ISTANBUL).toInstant(), ISTANBUL));

    @Test
    void newEventsStartInTheFutureAfterTheNoticeAndEndAfterTheyStart() {
        assertCode(() -> schedule.requireValidNewSchedule(NOW.minusHours(1), NOW.plusHours(1)), "EVENT_IN_PAST");
        assertCode(() -> schedule.requireValidNewSchedule(NOW.plusHours(5), NOW.plusHours(6)), "EVENT_TOO_SOON");
        assertCode(() -> schedule.requireValidNewSchedule(NOW.plusDays(2), NOW.plusDays(2)), "INVALID_EVENT_TIMES");
        assertThatCode(() -> schedule.requireValidNewSchedule(NOW.plusDays(2), NOW.plusDays(4))).doesNotThrowAnyException();
        assertThat(schedule.endOrDefault(NOW.plusDays(2), null)).isEqualTo(NOW.plusDays(2).plusHours(2));
    }

    @Test
    void checkInOpensAnHourBeforeAndClosesAtTheEnd() {
        assertCode(() -> schedule.requireCheckInOpen(event(NOW.plusMinutes(61), NOW.plusHours(3))), "CHECK_IN_CLOSED");
        assertThatCode(() -> schedule.requireCheckInOpen(event(NOW.plusMinutes(59), NOW.plusHours(3)))).doesNotThrowAnyException();
        assertThatCode(() -> schedule.requireCheckInOpen(event(NOW.minusDays(1), NOW.plusMinutes(1)))).doesNotThrowAnyException();
        assertCode(() -> schedule.requireCheckInOpen(event(NOW.minusHours(3), NOW.minusMinutes(1))), "CHECK_IN_CLOSED");
    }

    @Test
    void startedEventsCannotBeApproved() {
        assertCode(() -> schedule.requireNotStarted(event(NOW.minusMinutes(1), NOW.plusHours(1))), "EVENT_STARTED");
        assertThatCode(() -> schedule.requireNotStarted(event(NOW.plusMinutes(1), NOW.plusHours(1)))).doesNotThrowAnyException();
    }

    private static Event event(LocalDateTime startsAt, LocalDateTime endsAt) {
        Event event = new Event();
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        return event;
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }
}
