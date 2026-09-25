package com.educonnect.eventservice.service;

import com.educonnect.eventservice.Repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.Repository.EventRegistrationRepository;
import com.educonnect.eventservice.Repository.EventRepository;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.client.UserLookup;
import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.EventRegistrationMessage;
import com.educonnect.eventservice.dto.response.EventParticipationRequestDTO;
import com.educonnect.eventservice.dto.response.UserSummary;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final UserClient userClient;
    private final ClubClient clubClient;
    private final EventCaches eventCaches;

    public EventParticipationRequestService(
            EventParticipationRequestRepository participationRequestRepository,
            EventRepository eventRepository,
            EventRegistrationRepository eventRegistrationRepository,
            EventAuthorizationService eventAuthorizationService,
            OutboxPublisher outboxPublisher,
            UserClient userClient,
            ClubClient clubClient,
            EventCaches eventCaches) {
        this.participationRequestRepository = participationRequestRepository;
        this.eventRepository = eventRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.eventAuthorizationService = eventAuthorizationService;
        this.outboxPublisher = outboxPublisher;
        this.userClient = userClient;
        this.clubClient = clubClient;
        this.eventCaches = eventCaches;
    }

    /**
     * Öğrenci etkinliğe katılım isteği gönderir.
     * Kontroller:
     * 1. Etkinlik aktif mi?
     * 2. Öğrenci kulübün üyesi mi?
     * 3. Daha önce başvuru yapmış mı?
     * 4. Zaten kayıtlı mı?
     */
    public EventParticipationRequest createParticipationRequest(UUID eventId, UUID studentId, String message) {
        // 1. Etkinliği bul
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        // 2. Etkinlik aktif mi?
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu etkinlik artık aktif değil");
        }
        if (event.getEventTime() != null && event.getEventTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçmiş bir etkinliğe katılım isteği gönderilemez");
        }

        // 3. Öğrenci kulübün üyesi mi? (Club-service'e REST çağrısı)
        if (!isStudentMemberOfClub(studentId, event.getClubId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Bu etkinliğe katılabilmek için önce kulübe üye olmalısınız");
        }

        // 4. Zaten kayıtlı mı?
        if (eventRegistrationRepository.existsByEventIdAndStudentId(eventId, studentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu etkinliğe zaten kayıtlısınız");
        }

        // 5. Daha önce başvuru yapmış mı?
        EventParticipationRequest request = participationRequestRepository.findByEventIdAndStudentId(eventId, studentId)
                .orElse(null);
        if (request != null && request.getStatus() == ParticipationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu etkinlik için zaten bekleyen bir başvurunuz bulunuyor");
        }

        // 6. Yeni başvuru oluştur
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

    /**
     * Kulüp yetkilisi katılım isteğini onaylar.
     * Onay sonrası:
     * 1. EventRegistration oluşturulur (QR kod dahil)
     * 2. RabbitMQ ile bildirim gönderilir (mail için)
     */
    public EventRegistration approveParticipationRequest(UUID requestId, UUID approverId) {
        // 1. İsteği bul
        EventParticipationRequest request = participationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Katılım isteği bulunamadı"));

        // 2. Durumu kontrol et
        if (request.getStatus() != ParticipationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }

        // 3. Etkinliği bul
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        // 4. Onaylayan kişi yetkili mi? (Kulüp yetkilisi veya etkinliği oluşturan kişi)
        if (!isAuthorizedToManageEvent(approverId, event)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu isteği onaylama yetkiniz yok");
        }

        // 5. İsteği onayla
        request.setStatus(ParticipationRequestStatus.APPROVED);
        request.setProcessedDate(LocalDateTime.now());
        request.setProcessedBy(approverId);
        participationRequestRepository.save(request);

        // 6. Öğrenci bilgilerini user-service'den al
        String studentEmail = null;
        String studentNumber = null;
        try {
            UserSummary user = userClient.getUserById(request.getStudentId());
            if (user != null) {
                studentEmail = user.getEmail();
                studentNumber = user.getStudentNumber();
            }
        } catch (Exception e) {
            log.warn("Öğrenci bilgileri alınamadı: {}", e.getMessage());
        }

        // 7. Etkinlik kaydı oluştur (QR kod ile)
        EventRegistration registration = new EventRegistration();
        registration.setEventId(request.getEventId());
        registration.setStudentId(request.getStudentId());
        registration.setStudentEmail(studentEmail);
        registration.setStudentNumber(studentNumber);
        registration.setQrCode(UUID.randomUUID().toString());
        EventRegistration savedRegistration = eventRegistrationRepository.save(registration);
        eventCaches.evictStudentRegistrations(savedRegistration.getStudentId());

        // 8. RabbitMQ ile bildirim gönder (mail için)
        EventRegistrationMessage message = new EventRegistrationMessage(
                request.getStudentId(),
                event.getTitle(),
                event.getEventTime(),
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

    /**
     * Kulüp yetkilisi katılım isteğini reddeder.
     */
    public EventParticipationRequest rejectParticipationRequest(UUID requestId, UUID rejecterId, String rejectionReason) {
        // 1. İsteği bul
        EventParticipationRequest request = participationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Katılım isteği bulunamadı"));

        // 2. Durumu kontrol et
        if (request.getStatus() != ParticipationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }

        // 3. Etkinliği bul
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        // 4. Reddeden kişi yetkili mi?
        if (!isAuthorizedToManageEvent(rejecterId, event)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu isteği reddetme yetkiniz yok");
        }

        // 5. İsteği reddet
        request.setStatus(ParticipationRequestStatus.REJECTED);
        request.setProcessedDate(LocalDateTime.now());
        request.setProcessedBy(rejecterId);
        request.setRejectionReason(rejectionReason);

        EventParticipationRequest savedRequest = participationRequestRepository.save(request);
        log.info("Katılım isteği reddedildi: requestId={}, studentId={}", requestId, request.getStudentId());

        return savedRequest;
    }

    /**
     * Bir etkinliğin bekleyen katılım isteklerini getirir (Kulüp yetkilisi için).
     */
    public List<EventParticipationRequestDTO> getPendingRequestsForEvent(UUID eventId, UUID requesterId) {
        // 1. Etkinliği bul
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        // 2. Yetki kontrolü
        if (!isAuthorizedToManageEvent(requesterId, event)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu etkinliğin isteklerini görüntüleme yetkiniz yok");
        }

        // 3. Bekleyen istekleri getir
        List<EventParticipationRequest> requests = participationRequestRepository
                .findByEventIdAndStatus(eventId, ParticipationRequestStatus.PENDING);

        return enrichRequestsWithUserInfo(requests, event);
    }

    /**
     * Bir etkinliğin tüm katılım isteklerini getirir (Kulüp yetkilisi için).
     */
    public List<EventParticipationRequestDTO> getAllRequestsForEvent(UUID eventId, UUID requesterId) {
        // 1. Etkinliği bul
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        // 2. Yetki kontrolü
        if (!isAuthorizedToManageEvent(requesterId, event)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu etkinliğin isteklerini görüntüleme yetkiniz yok");
        }

        // 3. Tüm istekleri getir
        List<EventParticipationRequest> requests = participationRequestRepository.findByEventId(eventId);

        return enrichRequestsWithUserInfo(requests, event);
    }

    /**
     * Öğrencinin kendi katılım isteklerini getirir.
     */
    public List<EventParticipationRequestDTO> getStudentParticipationRequests(UUID studentId) {
        List<EventParticipationRequest> requests = participationRequestRepository.findByStudentId(studentId);
        List<UUID> eventIds = requests.stream().map(EventParticipationRequest::getEventId).distinct().toList();
        Map<UUID, Event> events = eventIds.isEmpty() ? Map.of() : eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        return requests.stream().map(request -> {
            EventParticipationRequestDTO dto = new EventParticipationRequestDTO();
            dto.setId(request.getId());
            dto.setEventId(request.getEventId());
            dto.setStudentId(request.getStudentId());
            dto.setStatus(request.getStatus());
            dto.setRequestDate(request.getRequestDate());
            dto.setProcessedDate(request.getProcessedDate());
            dto.setMessage(request.getMessage());
            dto.setRejectionReason(request.getRejectionReason());

            Event event = events.get(request.getEventId());
            if (event != null) {
                dto.setEventTitle(event.getTitle());
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Kulüp yetkilisinin yönettiği tüm etkinliklerin bekleyen isteklerini getirir.
     */
    public List<EventParticipationRequestDTO> getPendingRequestsForOfficialEvents(UUID officialId) {
        List<UUID> managedClubIds = eventAuthorizationService.accessesOf(officialId).stream()
                .filter(access -> access.has(EventAuthorizationService.MANAGE_EVENT_OPERATIONS))
                .map(access -> access.clubId())
                .toList();
        if (managedClubIds.isEmpty()) {
            return List.of();
        }
        List<Event> officialEvents = eventRepository.findByClubIdIn(managedClubIds);

        if (officialEvents.isEmpty()) {
            return List.of();
        }

        List<UUID> eventIds = officialEvents.stream().map(Event::getId).collect(Collectors.toList());

        // 2. Bu etkinliklerin bekleyen isteklerini getir
        List<EventParticipationRequest> requests = participationRequestRepository
                .findByEventIdInAndStatus(eventIds, ParticipationRequestStatus.PENDING);
        Map<UUID, Event> eventsById = officialEvents.stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        Map<UUID, UserSummary> users = UserLookup.usersById(userClient,
                requests.stream().map(EventParticipationRequest::getStudentId).toList());

        return requests.stream().map(request -> {
            EventParticipationRequestDTO dto = new EventParticipationRequestDTO();
            dto.setId(request.getId());
            dto.setEventId(request.getEventId());
            dto.setStudentId(request.getStudentId());
            dto.setStatus(request.getStatus());
            dto.setRequestDate(request.getRequestDate());
            dto.setMessage(request.getMessage());

            Event event = eventsById.get(request.getEventId());
            if (event != null) {
                dto.setEventTitle(event.getTitle());
            }
            applyStudentInfo(dto, users.get(request.getStudentId()));

            return dto;
        }).collect(Collectors.toList());
    }

    // ==================== HELPER METHODS ====================

    private void applyStudentInfo(EventParticipationRequestDTO dto, UserSummary user) {
        if (user != null) {
            dto.setStudentName(user.getFirstName() + " " + user.getLastName());
            dto.setStudentEmail(user.getEmail());
        } else {
            dto.setStudentName("Bilinmiyor");
            dto.setStudentEmail("N/A");
        }
    }

    /**
     * Öğrencinin kulüp üyesi olup olmadığını kontrol eder.
     */
    private boolean isStudentMemberOfClub(UUID studentId, UUID clubId) {
        try {
            return Boolean.TRUE.equals(clubClient.isStudentMemberOfClub(clubId, studentId));
        } catch (Exception e) {
            log.error("Club-service'e üyelik kontrolü yapılamadı: {}", e.getMessage());
            // Güvenli tarafta kal - üyelik doğrulanamadığında izin verme
            return false;
        }
    }

    /**
     * Kullanıcının etkinliği yönetme yetkisi olup olmadığını kontrol eder.
     */
    private boolean isAuthorizedToManageEvent(UUID userId, Event event) {
        return eventAuthorizationService.canManageEvent(event, userId);
    }

    /**
     * Katılım isteklerini kullanıcı bilgileriyle zenginleştirir.
     */
    private List<EventParticipationRequestDTO> enrichRequestsWithUserInfo(
            List<EventParticipationRequest> requests, Event event) {
        Map<UUID, UserSummary> users = UserLookup.usersById(userClient,
                requests.stream().map(EventParticipationRequest::getStudentId).toList());

        return requests.stream().map(request -> {
            EventParticipationRequestDTO dto = new EventParticipationRequestDTO();
            dto.setId(request.getId());
            dto.setEventId(request.getEventId());
            dto.setEventTitle(event.getTitle());
            dto.setStudentId(request.getStudentId());
            dto.setStatus(request.getStatus());
            dto.setRequestDate(request.getRequestDate());
            dto.setProcessedDate(request.getProcessedDate());
            dto.setMessage(request.getMessage());
            dto.setRejectionReason(request.getRejectionReason());
            applyStudentInfo(dto, users.get(request.getStudentId()));

            return dto;
        }).collect(Collectors.toList());
    }
}

