package com.educonnect.eventservice.service;

import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.ForbiddenException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.EventRegistrationMessage;
import com.educonnect.eventservice.dto.response.EventAvailability;
import com.educonnect.eventservice.model.AdmissionMode;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventAudience;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.educonnect.common.messaging.notification.NotificationCategory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;

@Service
@Transactional
public class EventParticipationRequestService {

    private static final Logger log = LoggerFactory.getLogger(EventParticipationRequestService.class);
    private static final Set<ParticipationRequestStatus> OPEN = Set.of(ParticipationRequestStatus.PENDING, ParticipationRequestStatus.WAITLISTED);

    private final EventParticipationRequestRepository participationRequestRepository;
    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final EventAuthorizationService eventAuthorizationService;
    private final OutboxPublisher outboxPublisher;
    private final ClubClient clubClient;
    private final EventCaches eventCaches;
    private final EventNotifier notifier;

    public EventParticipationRequestService(
            EventParticipationRequestRepository participationRequestRepository,
            EventRepository eventRepository,
            EventRegistrationRepository eventRegistrationRepository,
            EventAuthorizationService eventAuthorizationService,
            OutboxPublisher outboxPublisher,
            ClubClient clubClient,
            EventCaches eventCaches,
            EventNotifier notifier) {
        this.participationRequestRepository = participationRequestRepository;
        this.eventRepository = eventRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.eventAuthorizationService = eventAuthorizationService;
        this.outboxPublisher = outboxPublisher;
        this.clubClient = clubClient;
        this.eventCaches = eventCaches;
        this.notifier = notifier;
    }

    public EventParticipationRequest createParticipationRequest(UUID eventId, UUID studentId, String roles, String message) {
        Event event = lockedEvent(eventId);
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu etkinlik artık aktif değil");
        }
        requireRegistrationOpen(event);
        requireAudience(event, studentId, roles);
        if (eventRegistrationRepository.existsByEventIdAndStudentIdAndStatus(eventId, studentId, RegistrationStatus.REGISTERED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu etkinliğe zaten kayıtlısınız");
        }

        EventParticipationRequest request = participationRequestRepository.findByEventIdAndStudentId(eventId, studentId)
                .orElse(null);
        if (request != null && OPEN.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu etkinlik için zaten bekleyen bir başvurunuz bulunuyor");
        }
        if (request == null) {
            request = new EventParticipationRequest(eventId, studentId);
        } else {
            request.setRequestDate(Instant.now());
            request.setProcessedDate(null);
            request.setProcessedBy(null);
            request.setRejectionReason(null);
        }
        request.setMessage(message);

        if (event.getAdmission() == AdmissionMode.AUTO_CONFIRM) {
            if (hasSpace(event)) {
                request.setStatus(ParticipationRequestStatus.APPROVED);
                request.setProcessedDate(Instant.now());
                participationRequestRepository.save(request);
                register(event, studentId, EventRegistrationMessage.ORIGIN_SELF);
            } else {
                request.setStatus(ParticipationRequestStatus.WAITLISTED);
                participationRequestRepository.save(request);
            }
        } else {
            request.setStatus(ParticipationRequestStatus.PENDING);
            participationRequestRepository.save(request);
            notifier.notify(Collections.singletonList(event.getCreatedByStudentId()), NotificationCategory.CLUB_MANAGEMENT,
                    "EVENT_PARTICIPATION_REQUEST", event, "Yeni katılım talebi: " + event.getTitle(),
                    "\"" + event.getTitle() + "\" etkinliğine yeni bir katılım talebi var.");
        }
        log.info("Etkinlik katılım isteği: eventId={}, studentId={}, status={}", eventId, studentId, request.getStatus());
        return request;
    }

    public EventRegistration approveParticipationRequest(UUID requestId, UUID approverId) {
        EventParticipationRequest request = participationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Katılım isteği bulunamadı"));
        if (!OPEN.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }
        Event event = lockedEvent(request.getEventId());
        if (!eventAuthorizationService.canManageEvent(event, approverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu isteği onaylama yetkiniz yok");
        }
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Etkinlik artık aktif değil; istek onaylanamaz");
        }
        if (event.getStartsAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Geçmiş bir etkinlik için istek onaylanamaz");
        }
        if (event.getAudience() == EventAudience.MEMBERS_ONLY && !isStudentMemberOfClub(request.getStudentId(), event.getClubId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Öğrenci artık kulüp üyesi değil; istek onaylanamaz");
        }
        if (!hasSpace(event)) {
            throw new ConflictException("EVENT_FULL", "Etkinliğin kontenjanı dolu.");
        }

        request.setStatus(ParticipationRequestStatus.APPROVED);
        request.setProcessedDate(Instant.now());
        request.setProcessedBy(approverId);
        participationRequestRepository.save(request);
        EventRegistration registration = register(event, request.getStudentId(), EventRegistrationMessage.ORIGIN_REQUEST_APPROVED);
        log.info("Katılım isteği onaylandı: requestId={}, studentId={}", requestId, request.getStudentId());
        return registration;
    }

    public EventParticipationRequest rejectParticipationRequest(UUID requestId, UUID rejecterId, String rejectionReason) {
        EventParticipationRequest request = participationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Katılım isteği bulunamadı"));
        if (!OPEN.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));
        if (!eventAuthorizationService.canManageEvent(event, rejecterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu isteği reddetme yetkiniz yok");
        }

        request.setStatus(ParticipationRequestStatus.REJECTED);
        request.setProcessedDate(Instant.now());
        request.setProcessedBy(rejecterId);
        request.setRejectionReason(rejectionReason);
        EventParticipationRequest savedRequest = participationRequestRepository.save(request);
        notifier.notify(Collections.singletonList(request.getStudentId()), NotificationCategory.EVENT, "EVENT_REQUEST_REJECTED", event,
                "Katılım talebiniz reddedildi: " + event.getTitle(),
                "\"" + event.getTitle() + "\" etkinliği için katılım talebiniz reddedildi."
                        + (rejectionReason != null && !rejectionReason.isBlank() ? "\nGerekçe: " + rejectionReason.strip() : ""));
        log.info("Katılım isteği reddedildi: requestId={}, studentId={}", requestId, request.getStudentId());
        return savedRequest;
    }

    public void cancelRegistration(UUID eventId, UUID studentId) {
        Event event = lockedEvent(eventId);
        EventRegistration registration = eventRegistrationRepository.findByEventIdAndStudentId(eventId, studentId)
                .filter(EventRegistration::isActive)
                .orElseThrow(() -> new NotFoundException("REGISTRATION_NOT_FOUND", "Bu etkinliğe kaydınız yok."));
        LocalDateTime now = LocalDateTime.now();
        if (event.getStatus() != EventStatus.ACTIVE || now.isAfter(event.effectiveCancelUntil())) {
            throw new ConflictException("CANCELLATION_CLOSED", "Kayıt iptal süresi geçti.");
        }
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(Instant.now());
        eventRegistrationRepository.save(registration);
        participationRequestRepository.findByEventIdAndStudentId(eventId, studentId)
                .filter(request -> request.getStatus() == ParticipationRequestStatus.APPROVED)
                .ifPresent(request -> {
                    request.setStatus(ParticipationRequestStatus.WITHDRAWN);
                    participationRequestRepository.save(request);
                });
        eventCaches.evictStudentRegistrations(studentId);
        log.info("Etkinlik kaydı iptal edildi: eventId={}, studentId={}", eventId, studentId);
        promoteWaitlist(event);
    }

    public void withdrawRequest(UUID eventId, UUID studentId) {
        EventParticipationRequest request = participationRequestRepository.findByEventIdAndStudentId(eventId, studentId)
                .filter(candidate -> OPEN.contains(candidate.getStatus()))
                .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Bekleyen bir katılım isteğiniz yok."));
        request.setStatus(ParticipationRequestStatus.WITHDRAWN);
        request.setProcessedDate(Instant.now());
        participationRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public EventAvailability availability(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));
        long registered = eventRegistrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
        long waitlisted = participationRequestRepository.countByEventIdAndStatus(eventId, ParticipationRequestStatus.WAITLISTED);
        Integer remaining = event.getCapacity() == null ? null : (int) Math.max(0, event.getCapacity() - registered);
        LocalDateTime now = LocalDateTime.now();
        boolean open = event.getStatus() == EventStatus.ACTIVE
                && (event.getRegistrationOpensAt() == null || !now.isBefore(event.getRegistrationOpensAt()))
                && now.isBefore(event.effectiveRegistrationClose());
        return new EventAvailability(event.getId(), event.getAudience(), event.getAdmission(), event.getCapacity(), registered,
                waitlisted, remaining, open, event.getRegistrationOpensAt(), event.effectiveRegistrationClose(), event.effectiveCancelUntil());
    }

    private void promoteWaitlist(Event event) {
        if (event.getAdmission() != AdmissionMode.AUTO_CONFIRM || event.getStatus() != EventStatus.ACTIVE) {
            return;
        }
        while (hasSpace(event)) {
            Optional<EventParticipationRequest> next = participationRequestRepository
                    .findFirstByEventIdAndStatusOrderByRequestDateAsc(event.getId(), ParticipationRequestStatus.WAITLISTED);
            if (next.isEmpty()) {
                return;
            }
            EventParticipationRequest request = next.get();
            request.setStatus(ParticipationRequestStatus.APPROVED);
            request.setProcessedDate(Instant.now());
            participationRequestRepository.saveAndFlush(request);
            register(event, request.getStudentId(), EventRegistrationMessage.ORIGIN_WAITLIST_PROMOTED);
            log.info("Bekleme listesinden kayıt: eventId={}, studentId={}", event.getId(), request.getStudentId());
        }
    }

    private EventRegistration register(Event event, UUID studentId, String origin) {
        EventRegistration registration = eventRegistrationRepository.findByEventIdAndStudentId(event.getId(), studentId)
                .orElseGet(EventRegistration::new);
        registration.setEventId(event.getId());
        registration.setStudentId(studentId);
        registration.setQrCode(UUID.randomUUID().toString());
        registration.setRegistrationTime(Instant.now());
        registration.setAttended(false);
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setCancelledAt(null);
        EventRegistration saved = eventRegistrationRepository.saveAndFlush(registration);
        eventCaches.evictStudentRegistrations(studentId);
        outboxPublisher.publish(EventRabbitMQConfig.CLUB_EXCHANGE_NAME, EventRabbitMQConfig.ROUTING_KEY_EVENT_REGISTERED,
                new EventRegistrationMessage(studentId, event.getTitle(), event.getStartsAt(), event.getLocation(), saved.getQrCode(),
                        origin));
        return saved;
    }

    private boolean hasSpace(Event event) {
        return event.getCapacity() == null
                || eventRegistrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED) < event.getCapacity();
    }

    private void requireRegistrationOpen(Event event) {
        LocalDateTime now = LocalDateTime.now();
        if (event.getRegistrationOpensAt() != null && now.isBefore(event.getRegistrationOpensAt())) {
            throw new ConflictException("REGISTRATION_NOT_OPEN", "Bu etkinliğin kaydı henüz açılmadı.");
        }
        if (!now.isBefore(event.effectiveRegistrationClose())) {
            throw new ConflictException("REGISTRATION_CLOSED", "Bu etkinliğin kaydı kapandı.");
        }
    }

    private void requireAudience(Event event, UUID studentId, String roles) {
        switch (event.getAudience()) {
            case MEMBERS_ONLY -> {
                if (!isStudentMemberOfClub(studentId, event.getClubId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu etkinliğe katılabilmek için önce kulübe üye olmalısınız");
                }
            }
            case ALL_STUDENTS -> {
                if (roles == null || Arrays.stream(roles.split(",")).map(String::trim).noneMatch("ROLE_STUDENT"::equals)) {
                    throw new ForbiddenException("STUDENTS_ONLY", "Bu etkinlik yalnız öğrencilere açık.");
                }
            }
            case CAMPUS -> {
            }
        }
    }

    private Event lockedEvent(UUID eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));
    }

    private boolean isStudentMemberOfClub(UUID studentId, UUID clubId) {
        try {
            return Boolean.TRUE.equals(clubClient.isStudentMemberOfClub(clubId, studentId));
        } catch (Exception e) {
            log.error("Club-service'e üyelik kontrolü yapılamadı: {}", e.getMessage());
            return false;
        }
    }
}
