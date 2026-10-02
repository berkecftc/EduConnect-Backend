package com.educonnect.eventservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.eventservice.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class EventSchedule {

    private final Duration minimumNotice;
    private final Duration checkInOpensBefore;
    private final Duration defaultDuration;
    private final Clock clock;

    @Autowired
    public EventSchedule(@Value("${educonnect.event.min-notice-hours:0}") long minimumNoticeHours,
                         @Value("${educonnect.event.check-in-opens-minutes:60}") long checkInOpensMinutes,
                         @Value("${educonnect.event.default-duration-minutes:120}") long defaultDurationMinutes) {
        this(Duration.ofHours(minimumNoticeHours), Duration.ofMinutes(checkInOpensMinutes),
                Duration.ofMinutes(defaultDurationMinutes), Clock.systemDefaultZone());
    }

    EventSchedule(Duration minimumNotice, Duration checkInOpensBefore, Duration defaultDuration, Clock clock) {
        this.minimumNotice = minimumNotice;
        this.checkInOpensBefore = checkInOpensBefore;
        this.defaultDuration = defaultDuration;
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDateTime endOrDefault(LocalDateTime startsAt, LocalDateTime endsAt) {
        return endsAt != null ? endsAt : startsAt.plus(defaultDuration);
    }

    public void requireValidNewSchedule(LocalDateTime startsAt, LocalDateTime endsAt) {
        LocalDateTime now = now();
        if (!startsAt.isAfter(now)) {
            throw new BadRequestException("EVENT_IN_PAST", "Etkinlik başlangıcı gelecekte olmalı.");
        }
        if (startsAt.isBefore(now.plus(minimumNotice))) {
            throw new BadRequestException("EVENT_TOO_SOON",
                    "Etkinlik en az " + minimumNotice.toHours() + " saat önceden hazırlanmalı.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new BadRequestException("INVALID_EVENT_TIMES", "Etkinlik bitişi başlangıcından sonra olmalı.");
        }
    }

    public void requireValidRegistration(LocalDateTime startsAt, LocalDateTime opensAt, LocalDateTime closesAt, LocalDateTime cancelUntil) {
        LocalDateTime close = closesAt != null ? closesAt : startsAt;
        if (close.isAfter(startsAt) || (opensAt != null && !opensAt.isBefore(close))
                || (cancelUntil != null && cancelUntil.isAfter(startsAt))) {
            throw new BadRequestException("INVALID_REGISTRATION_WINDOW",
                    "Kayıt penceresi başlangıçtan önce kapanmalı, açılış kapanıştan önce olmalı ve iptal süresi başlangıcı geçmemeli.");
        }
    }

    public void requireNotStarted(Event event) {
        if (!event.getStartsAt().isAfter(now())) {
            throw new ConflictException("EVENT_STARTED", "Başlama saati geçmiş bir etkinlik onaylanamaz.");
        }
    }

    public void requireCheckInOpen(Event event) {
        LocalDateTime now = now();
        if (now.isBefore(event.getStartsAt().minus(checkInOpensBefore)) || now.isAfter(event.getEndsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "CHECK_IN_CLOSED",
                    "Giriş yalnız etkinlikten " + checkInOpensBefore.toMinutes() + " dakika önce açılır ve etkinlik bitince kapanır.");
        }
    }
}
