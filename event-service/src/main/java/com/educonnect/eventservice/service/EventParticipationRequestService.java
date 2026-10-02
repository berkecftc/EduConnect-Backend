package com.educonnect.eventservice.service;

import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.EventRegistrationMessage;
import com.educonnect.eventservice.model.*;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Etkinlik katılım istekleri için servis katmanı.
 * Öğrenci üye olduğu kulübün etkinliklerine katılım isteği gönderir,
 * kulüp başkanı onayladıktan sonra kayıt + QR kod oluşturulur.
 */
@Service
@Transactional
public class EventParticipationRequestService {

    private static final Logger log = LoggerFactory.getLogger(EventParticipationRequestService.class);

    private final EventParticipationRequestRepository participationRequestRepository;
    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final EventAuthorizationService eventAuthorizationService;
    private final OutboxPublisher outboxPublisher;
    private final ClubClient clubClient;
    private final EventCaches eventCaches;

    public EventParticipationRequestService(
            EventParticipationRequestRepository participationRequestRepository,
            EventRepository eventRepository,
            EventRegistrationRepository eventRegistrationRepository,
            EventAuthorizationService eventAuthorizationService,
            OutboxPublisher outboxPublisher,
            ClubClient clubClient,
            EventCaches eventCaches) {
        this.participationRequestRepository = participationRequestRepository;
        this.eventRepository = eventRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.eventAuthorizationService = eventAuthorizationService;
        this.outboxPublisher = outboxPublisher;
        this.clubClient = clubClient;
        this.eventCaches = eventCaches;
    }

    public EventParticipationRequest createParticipationRequest(UUID eventId, UUID studentId, String message) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu etkinlik artık aktif değil");
        }
        if (event.getStartsAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçmiş bir etkinliğe katılım isteği gönderilemez");
        }

        if (!isStudentMemberOfClub(studentId, event.getClubId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Bu etkinliğe katılabilmek için önce kulübe üye olmalısınız");
        }

        if (eventRegistrationRepository.existsByEventIdAndStudentId(eventId, studentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu etkinliğe zaten kayıtlısınız");
        }

        EventParticipationRequest request = participationRequestRepository.findByEventIdAndStudentId(eventId, studentId)
                .orElse(null);
        if (request != null && request.getStatus() == ParticipationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu etkinlik için zaten bekleyen bir başvurunuz bulunuyor");
        }

        if (request == null) {
            request = new EventParticipationRequest(eventId, studentId);
        } else {
            request.setStatus(ParticipationRequestStatus.PENDING);
            request.setRequestDate(LocalDateTime.now());
            request.setProcessedDate(null);
            request.setProcessedBy(null);
            request.setRejectionReason(null);
        }
        request.setMessage(message);

        EventParticipationRequest savedRequest = participationRequestRepository.save(request);
        log.info("Etkinlik katılım isteği oluşturuldu: eventId={}, studentId={}", eventId, studentId);

        return savedRequest;
    }

    public EventRegistration approveParticipationRequest(UUID requestId, UUID approverId) {
        EventParticipationRequest request = participationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Katılım isteği bulunamadı"));

        if (request.getStatus() != ParticipationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        if (!eventAuthorizationService.canManageEvent(event, approverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu isteği onaylama yetkiniz yok");
        }
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Etkinlik artık aktif değil; istek onaylanamaz");
        }
        if (event.getStartsAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Geçmiş bir etkinlik için istek onaylanamaz");
        }
        if (!isStudentMemberOfClub(request.getStudentId(), event.getClubId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Öğrenci artık kulüp üyesi değil; istek onaylanamaz");
        }

        request.setStatus(ParticipationRequestStatus.APPROVED);
        request.setProcessedDate(LocalDateTime.now());
        request.setProcessedBy(approverId);
        participationRequestRepository.save(request);

        EventRegistration registration = new EventRegistration();
        registration.setEventId(request.getEventId());
        registration.setStudentId(request.getStudentId());
        registration.setQrCode(UUID.randomUUID().toString());
        EventRegistration savedRegistration = eventRegistrationRepository.save(registration);
        eventCaches.evictStudentRegistrations(savedRegistration.getStudentId());

        EventRegistrationMessage message = new EventRegistrationMessage(
                request.getStudentId(),
                event.getTitle(),
                event.getStartsAt(),
                event.getLocation(),
                savedRegistration.getQrCode()
        );

        outboxPublisher.publish(
                EventRabbitMQConfig.CLUB_EXCHANGE_NAME,
                EventRabbitMQConfig.ROUTING_KEY_EVENT_REGISTERED,
                message
        );

        log.info("Katılım isteği onaylandı ve QR kod maili gönderildi: requestId={}, studentId={}",
                requestId, request.getStudentId());

        return savedRegistration;
    }

    public EventParticipationRequest rejectParticipationRequest(UUID requestId, UUID rejecterId, String rejectionReason) {
        EventParticipationRequest request = participationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Katılım isteği bulunamadı"));

        if (request.getStatus() != ParticipationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        if (!eventAuthorizationService.canManageEvent(event, rejecterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu isteği reddetme yetkiniz yok");
        }

        request.setStatus(ParticipationRequestStatus.REJECTED);
        request.setProcessedDate(LocalDateTime.now());
        request.setProcessedBy(rejecterId);
        request.setRejectionReason(rejectionReason);

        EventParticipationRequest savedRequest = participationRequestRepository.save(request);
        log.info("Katılım isteği reddedildi: requestId={}, studentId={}", requestId, request.getStudentId());

        return savedRequest;
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
