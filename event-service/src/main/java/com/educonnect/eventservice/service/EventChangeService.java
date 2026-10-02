package com.educonnect.eventservice.service;

import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.ForbiddenException;
import com.educonnect.eventservice.config.ApprovalChainSettings;
import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.EventChangedMessage;
import com.educonnect.eventservice.dto.request.EventChangeRequest;
import com.educonnect.eventservice.dto.request.UpdateEventRequest;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.dto.response.EventChangeResponse;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventAudience;
import com.educonnect.eventservice.model.EventChange;
import com.educonnect.eventservice.model.EventChangeKind;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventChangeRepository;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class EventChangeService {

    private static final Set<EventStatus> DRAFTS = Set.of(EventStatus.PENDING_PRESIDENT, EventStatus.PENDING, EventStatus.REJECTED);
    private static final Set<EventStatus> CANCELLABLE = Set.of(EventStatus.PENDING_PRESIDENT, EventStatus.PENDING, EventStatus.ACTIVE);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventParticipationRequestRepository requestRepository;
    private final EventChangeRepository changeRepository;
    private final EventAuthorizationService authorizationService;
    private final EventSchedule schedule;
    private final ApprovalChainSettings approvalChain;
    private final EventCaches eventCaches;
    private final OutboxPublisher outboxPublisher;

    public EventChangeService(EventRepository eventRepository,
                              EventRegistrationRepository registrationRepository,
                              EventParticipationRequestRepository requestRepository,
                              EventChangeRepository changeRepository,
                              EventAuthorizationService authorizationService,
                              EventSchedule schedule,
                              ApprovalChainSettings approvalChain,
                              EventCaches eventCaches,
                              OutboxPublisher outboxPublisher) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.requestRepository = requestRepository;
        this.changeRepository = changeRepository;
        this.authorizationService = authorizationService;
        this.schedule = schedule;
        this.approvalChain = approvalChain;
        this.eventCaches = eventCaches;
        this.outboxPublisher = outboxPublisher;
    }

    public Event update(UUID eventId, UUID actorId, UpdateEventRequest request) {
        Event event = event(eventId);
        ClubAccess access = requirePreparer(event, actorId);
        if (!DRAFTS.contains(event.getStatus())) {
            throw new ConflictException("EVENT_NOT_EDITABLE",
                    "Onaylanmış etkinlik düzenlenemez; erteleme, yer değişikliği veya iptal kullanın.");
        }
        boolean resubmission = event.getStatus() == EventStatus.REJECTED;
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        if (resubmission && note == null) {
            throw new BadRequestException("RESUBMISSION_NOTE_REQUIRED", "Reddedilen etkinliği yeniden göndermek için düzeltme notu zorunludur.");
        }
        if (request.audience() == EventAudience.CAMPUS) {
            throw new BadRequestException("AUDIENCE_NOT_ALLOWED", "Kulüp etkinliği yalnız üyelere veya tüm öğrencilere açılabilir.");
        }

        if (request.title() != null && !request.title().isBlank()) {
            event.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            event.setDescription(request.description());
        }
        if (request.location() != null) {
            event.setLocation(request.location().isBlank() ? null : request.location().strip());
        }
        if (request.speakers() != null) {
            event.setSpeakers(request.speakers().isBlank() ? null : request.speakers().strip());
        }
        if (request.startsAt() != null || request.endsAt() != null) {
            LocalDateTime startsAt = request.startsAt() != null ? request.startsAt() : event.getStartsAt();
            LocalDateTime endsAt = request.endsAt() != null ? request.endsAt()
                    : schedule.endOrDefault(startsAt, null);
            schedule.requireValidNewSchedule(startsAt, endsAt);
            event.setStartsAt(startsAt);
            event.setEndsAt(endsAt);
        }
        if (request.audience() != null) {
            event.setAudience(request.audience());
        }
        if (request.admission() != null) {
            event.setAdmission(request.admission());
        }
        if (request.capacity() != null) {
            event.setCapacity(request.capacity());
        }
        if (request.registrationOpensAt() != null) {
            event.setRegistrationOpensAt(request.registrationOpensAt());
        }
        if (request.registrationClosesAt() != null) {
            event.setRegistrationClosesAt(request.registrationClosesAt());
        }
        if (request.cancelUntil() != null) {
            event.setCancelUntil(request.cancelUntil());
        }
        schedule.requireValidRegistration(event.getStartsAt(), event.getRegistrationOpensAt(), event.getRegistrationClosesAt(),
                event.getCancelUntil());

        if (resubmission) {
            event.setRejectionReason(null);
            event.setStatus(approvalChain.enabled() && !access.has(EventAuthorizationService.APPROVE_AS_PRESIDENT)
                    ? EventStatus.PENDING_PRESIDENT
                    : EventStatus.PENDING);
        }
        Event saved = eventRepository.save(event);
        changeRepository.save(new EventChange(eventId, resubmission ? EventChangeKind.RESUBMITTED : EventChangeKind.EDITED,
                null, note, actorId));
        eventCaches.evictEvent(saved);
        return saved;
    }

    public Event postpone(UUID eventId, UUID actorId, EventChangeRequest request) {
        Event event = event(eventId);
        authorizationService.require(event.getClubId(), actorId, EventAuthorizationService.CREATE_EVENT);
        requireActiveNotStarted(event);
        if (request.startsAt() == null) {
            throw new BadRequestException("NEW_START_REQUIRED", "Yeni başlangıç zamanı zorunludur.");
        }
        LocalDateTime endsAt = request.endsAt() != null ? request.endsAt()
                : request.startsAt().plus(Duration.between(event.getStartsAt(), event.getEndsAt()));
        schedule.requireValidNewSchedule(request.startsAt(), endsAt);
        String details = event.getStartsAt() + " → " + request.startsAt();
        event.setStartsAt(request.startsAt());
        event.setEndsAt(endsAt);
        if (event.getRegistrationClosesAt() != null && event.getRegistrationClosesAt().isAfter(request.startsAt())) {
            event.setRegistrationClosesAt(null);
        }
        if (event.getCancelUntil() != null && event.getCancelUntil().isAfter(request.startsAt())) {
            event.setCancelUntil(null);
        }
        event.setStatus(EventStatus.PENDING);
        Event saved = eventRepository.save(event);
        record(saved, EventChangeKind.POSTPONED, details, request.reason(), actorId);
        return saved;
    }

    public Event relocate(UUID eventId, UUID actorId, EventChangeRequest request) {
        Event event = event(eventId);
        authorizationService.require(event.getClubId(), actorId, EventAuthorizationService.CREATE_EVENT);
        if (event.getStatus() != EventStatus.ACTIVE || !event.getEndsAt().isAfter(schedule.now())) {
            throw new ConflictException("EVENT_NOT_CHANGEABLE", "Yalnız yayımlanmış ve bitmemiş etkinliğin yeri değiştirilebilir.");
        }
        if (request.location() == null || request.location().isBlank()) {
            throw new BadRequestException("NEW_LOCATION_REQUIRED", "Yeni yer zorunludur.");
        }
        String details = event.getLocation() + " → " + request.location().strip();
        event.setLocation(request.location().strip());
        Event saved = eventRepository.save(event);
        record(saved, EventChangeKind.RELOCATED, details, request.reason(), actorId);
        return saved;
    }

    public Event cancel(UUID eventId, UUID actorId, EventChangeRequest request) {
        Event event = event(eventId);
        ClubAccess access = authorizationService.accessOf(event.getClubId(), actorId);
        if (!access.has(EventAuthorizationService.CREATE_EVENT) && !access.has(EventAuthorizationService.ADVISE)) {
            throw new ForbiddenException("Etkinliği yalnız kulüp başkanı veya danışmanı iptal edebilir.");
        }
        if (!CANCELLABLE.contains(event.getStatus()) || !event.getEndsAt().isAfter(schedule.now())) {
            throw new ConflictException("EVENT_NOT_CHANGEABLE", "Bu etkinlik artık iptal edilemez.");
        }
        event.setStatus(EventStatus.CANCELLED);
        event.setCancellationReason(request.reason().strip());
        Event saved = eventRepository.save(event);
        for (ParticipationRequestStatus open : List.of(ParticipationRequestStatus.PENDING, ParticipationRequestStatus.WAITLISTED)) {
            for (EventParticipationRequest pending : requestRepository.findByEventIdAndStatus(eventId, open)) {
                pending.setStatus(ParticipationRequestStatus.CLOSED);
                pending.setProcessedDate(LocalDateTime.now());
                requestRepository.save(pending);
            }
        }
        record(saved, EventChangeKind.CANCELLED, null, request.reason(), actorId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EventChangeResponse> changes(UUID eventId, UUID viewerId) {
        Event event = event(eventId);
        authorizationService.requireEventViewer(event, viewerId);
        return changeRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream().map(EventChangeResponse::of).toList();
    }

    private void record(Event event, EventChangeKind kind, String details, String reason, UUID actorId) {
        changeRepository.save(new EventChange(event.getId(), kind, details, reason == null ? null : reason.strip(), actorId));
        List<UUID> recipients = registrationRepository.findByEventId(event.getId()).stream()
                .filter(registration -> registration.getStatus() == RegistrationStatus.REGISTERED)
                .map(EventRegistration::getStudentId)
                .toList();
        recipients.forEach(eventCaches::evictStudentRegistrations);
        eventCaches.evictEvent(event);
        if (!recipients.isEmpty()) {
            outboxPublisher.publish(EventRabbitMQConfig.CLUB_EXCHANGE_NAME, EventRabbitMQConfig.ROUTING_KEY_EVENT_CHANGED,
                    new EventChangedMessage(event.getId(), event.getTitle(), event.getClubName(), kind.name(), event.getStartsAt(),
                            event.getEndsAt(), event.getLocation(), reason, recipients));
        }
    }

    private ClubAccess requirePreparer(Event event, UUID actorId) {
        ClubAccess access = authorizationService.accessOf(event.getClubId(), actorId);
        if (!access.has(EventAuthorizationService.PREPARE_EVENT) && !access.has(EventAuthorizationService.CREATE_EVENT)) {
            throw new ForbiddenException("Bu etkinliği düzenleme yetkiniz yok.");
        }
        return access;
    }

    private void requireActiveNotStarted(Event event) {
        if (event.getStatus() != EventStatus.ACTIVE || !event.getStartsAt().isAfter(schedule.now())) {
            throw new ConflictException("EVENT_NOT_CHANGEABLE", "Yalnız yayımlanmış ve başlamamış etkinlik ertelenebilir.");
        }
    }

    private Event event(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));
    }
}
