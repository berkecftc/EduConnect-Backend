package com.educonnect.eventservice.service;

import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.client.UserLookup;
import com.educonnect.eventservice.dto.response.AttendanceReport;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.dto.response.UserSummary;
import com.educonnect.eventservice.model.CheckInMethod;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AttendanceService {

    private static final char BOM = (char) 0xFEFF;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventAuthorizationService authorizationService;
    private final EventSchedule schedule;
    private final EventCaches eventCaches;
    private final UserClient userClient;

    public AttendanceService(EventRepository eventRepository,
                             EventRegistrationRepository registrationRepository,
                             EventAuthorizationService authorizationService,
                             EventSchedule schedule,
                             EventCaches eventCaches,
                             UserClient userClient) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.authorizationService = authorizationService;
        this.schedule = schedule;
        this.eventCaches = eventCaches;
        this.userClient = userClient;
    }

    public void checkIn(UUID eventId, UUID studentId, UUID actorId) {
        Event event = event(eventId);
        authorizationService.requireCheckInStaff(event, actorId);
        requireLive(event);
        EventRegistration registration = activeRegistration(eventId, studentId);
        if (registration.isAttended()) {
            throw new ConflictException("ALREADY_CHECKED_IN", "Katılımcının girişi zaten yapılmış.");
        }
        registration.checkIn(actorId, CheckInMethod.MANUAL, schedule.now());
        registrationRepository.save(registration);
        eventCaches.evictStudentRegistrations(studentId);
    }

    public void undoCheckIn(UUID eventId, UUID studentId, UUID actorId) {
        Event event = event(eventId);
        authorizationService.requireCheckInStaff(event, actorId);
        requireLive(event);
        EventRegistration registration = activeRegistration(eventId, studentId);
        if (!registration.isAttended()) {
            throw new ConflictException("NOT_CHECKED_IN", "Katılımcının girişi yapılmamış.");
        }
        registration.undoCheckIn();
        registrationRepository.save(registration);
        eventCaches.evictStudentRegistrations(studentId);
    }

    @Transactional(readOnly = true)
    public AttendanceReport report(UUID eventId, UUID viewerId) {
        Event event = event(eventId);
        authorizationService.requireEventViewer(event, viewerId);
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        Map<UUID, UserSummary> users = UserLookup.usersById(userClient,
                registrations.stream().map(EventRegistration::getStudentId).distinct().toList());
        List<AttendanceReport.Row> rows = registrations.stream()
                .map(registration -> {
                    UserSummary user = users.get(registration.getStudentId());
                    return new AttendanceReport.Row(registration.getStudentId(),
                            user != null ? user.getFirstName() : null, user != null ? user.getLastName() : null,
                            user != null ? user.getStudentNumber() : null, registration.getStatus(), registration.isAttended(),
                            registration.getCheckedInAt(), registration.getCheckedInBy(), registration.getCheckInMethod());
                })
                .sorted(Comparator.comparing((AttendanceReport.Row row) -> row.lastName() == null ? "" : row.lastName())
                        .thenComparing(row -> row.firstName() == null ? "" : row.firstName()))
                .toList();
        return new AttendanceReport(event.getId(), event.getTitle(), event.getStatus(), event.getStartsAt(),
                count(registrations, RegistrationStatus.REGISTERED) + count(registrations, RegistrationStatus.NO_SHOW),
                registrations.stream().filter(EventRegistration::isAttended).count(),
                count(registrations, RegistrationStatus.NO_SHOW),
                count(registrations, RegistrationStatus.CANCELLED),
                rows);
    }

    @Transactional(readOnly = true)
    public String reportCsv(UUID eventId, UUID viewerId) {
        AttendanceReport report = report(eventId, viewerId);
        StringBuilder csv = new StringBuilder().append(BOM).append("Ad;Soyad;Öğrenci No;Kayıt Durumu;Katıldı;Giriş Zamanı;Giriş Yöntemi\n");
        for (AttendanceReport.Row row : report.rows()) {
            csv.append(cell(row.firstName())).append(';')
                    .append(cell(row.lastName())).append(';')
                    .append(cell(row.studentNumber())).append(';')
                    .append(row.status()).append(';')
                    .append(row.attended() ? "Evet" : "Hayır").append(';')
                    .append(row.checkedInAt() == null ? "" : CSV_TIME.format(row.checkedInAt())).append(';')
                    .append(row.method() == null ? "" : row.method())
                    .append('\n');
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public List<AttendanceReport.EventLine> clubReport(UUID clubId, LocalDateTime from, LocalDateTime to, UUID viewerId) {
        ClubAccess access = authorizationService.accessOf(clubId, viewerId);
        if (!access.has(EventAuthorizationService.MANAGE_EVENT_OPERATIONS) && !access.has(EventAuthorizationService.ADVISE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu kulübün katılım dökümünü görme yetkiniz yok.");
        }
        return eventRepository.findByClubIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(clubId, from, to).stream()
                .filter(event -> event.getStatus() == EventStatus.ACTIVE || event.getStatus() == EventStatus.COMPLETED)
                .sorted(Comparator.comparing(Event::getStartsAt))
                .map(event -> {
                    List<EventRegistration> registrations = registrationRepository.findByEventId(event.getId());
                    return new AttendanceReport.EventLine(event.getId(), event.getTitle(), event.getStartsAt(), event.getStatus(),
                            count(registrations, RegistrationStatus.REGISTERED) + count(registrations, RegistrationStatus.NO_SHOW),
                            registrations.stream().filter(EventRegistration::isAttended).count(),
                            count(registrations, RegistrationStatus.NO_SHOW));
                })
                .toList();
    }

    private void requireLive(Event event) {
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ConflictException("EVENT_NOT_ACTIVE", "Etkinlik aktif değil.");
        }
        schedule.requireCheckInOpen(event);
    }

    private EventRegistration activeRegistration(UUID eventId, UUID studentId) {
        return registrationRepository.findByEventIdAndStudentId(eventId, studentId)
                .filter(EventRegistration::isActive)
                .orElseThrow(() -> new NotFoundException("REGISTRATION_NOT_FOUND", "Bu öğrencinin etkin kaydı yok."));
    }

    private Event event(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));
    }

    private static long count(List<EventRegistration> registrations, RegistrationStatus status) {
        return registrations.stream().filter(registration -> registration.getStatus() == status).count();
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(";") || escaped.contains("\"") || escaped.contains("\n") ? "\"" + escaped + "\"" : escaped;
    }
}
