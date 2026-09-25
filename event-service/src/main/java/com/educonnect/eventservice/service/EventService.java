package com.educonnect.eventservice.service;

import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.client.UserLookup;
import com.educonnect.eventservice.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.EventCreatedMessage;
import com.educonnect.eventservice.dto.MyEventRegistrationDTO;
import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.dto.response.EventRegistrantDTO;
import com.educonnect.eventservice.dto.response.UserSummary;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.Repository.EventRepository;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.Repository.EventRegistrationRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final MinioService minioService;
    private final OutboxPublisher outboxPublisher;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final RestTemplate restTemplate;
    private final UserClient userClient;
    private final ClubClient clubClient;
    private final EventAuthorizationService eventAuthorizationService;
    private final EventCaches eventCaches;

    public EventService(EventRepository eventRepository,
                       MinioService minioService,
                       OutboxPublisher outboxPublisher,
                       EventRegistrationRepository eventRegistrationRepository,
                       RestTemplate restTemplate,
                       UserClient userClient,
                       ClubClient clubClient,
                       EventAuthorizationService eventAuthorizationService,
                       EventCaches eventCaches) {
        this.eventRepository = eventRepository;
        this.minioService = minioService;
        this.outboxPublisher = outboxPublisher;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.restTemplate = restTemplate;
        this.userClient = userClient;
        this.clubClient = clubClient;
        this.eventAuthorizationService = eventAuthorizationService;
        this.eventCaches = eventCaches;
    }

    /**
     * Yeni bir etkinlik oluşturur ve afişini yükler.
     * Afiş (posterFile) zorunludur.
     */
    public Event createEvent(CreateEventRequest request, MultipartFile posterFile, UUID creatorId) {
        // Afiş zorunlu kontrolü
        if (posterFile == null || posterFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etkinlik afişi zorunludur.");
        }
        minioService.validateImage(posterFile);

        URI clubServiceUrl = UriComponentsBuilder.fromUriString("http://CLUB-SERVICE/api/clubs/search")
                .queryParam("name", "{name}")
                .encode()
                .buildAndExpand(request.getClubName())
                .toUri();

        UUID resolvedClubId;
        try {
            // Karşı servisten gelen cevabı (DTO'yu) al
            // ClubSummaryDTO benzeri bir iç sınıf veya Map kullanabiliriz
            // Pratiklik adına Map kullanıyorum:
            Map<String, Object> response = restTemplate.getForObject(clubServiceUrl, Map.class);

            // ID'yi çek (String gelir, UUID'ye çevir)
            String idString = (String) response.get("id");
            resolvedClubId = UUID.fromString(idString);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid club name: " + request.getClubName() + ". Club not found.");
        }

        eventAuthorizationService.require(resolvedClubId, creatorId, EventAuthorizationService.CREATE_EVENT);

        // 1. Event Entity'sini oluştur
        Event event = new Event();
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setEventTime(request.getEventTime());
        event.setLocation(request.getLocation());
        event.setClubName(request.getClubName());
        event.setCreatedByStudentId(creatorId);
        event.setClubId(resolvedClubId);
        event.setStatus(EventStatus.PENDING);

        // 2. Önce veritabanına kaydet (ID oluşsun diye)
        Event savedEvent = eventRepository.save(event);

        // 3. Afişi MinIO'ya yükle (events/event-id.jpg) - Afiş zorunludur
        log.info("Uploading poster for event: {}", savedEvent.getId());
        String objectName = minioService.uploadFile(posterFile, "events", savedEvent.getId().toString());
        log.info("Poster uploaded successfully. URL: {}", objectName);
        savedEvent.setImageUrl(objectName);
        savedEvent = eventRepository.save(savedEvent); // URL ile tekrar güncelle ve sonucu al
        log.info("Event saved with imageUrl: {}", savedEvent.getImageUrl());
        eventCaches.evictEventListings(savedEvent);

        return savedEvent;
    }

    /**
     * Danışman Akademisyenin Tüm Etkinliklerini Listele
     * Akademisyenin danışmanı olduğu kulüplerin tüm etkinliklerini getirir (tüm durumlar dahil).
     *
     * @param advisorId Danışman akademisyen ID'si
     * @return Tüm etkinlik listesi
     */
    public List<Event> getAllEventsForAdvisor(UUID advisorId) {
        // 1. Akademisyenin danışmanı olduğu kulüpleri al
        List<UUID> clubIds = clubClient.getClubIdsByAdvisorId(advisorId);

        if (clubIds == null || clubIds.isEmpty()) {
            return List.of(); // Hiçbir kulübün danışmanı değilse boş liste dön
        }

        // 2. Bu kulüplere ait tüm etkinlikleri getir
        return eventRepository.findByClubIdIn(clubIds);
    }

    /**
     * Bekleyen Etkinlikleri Listele (Danışman Akademisyen İçin)
     * Sadece akademisyenin danışmanı olduğu kulüplerin bekleyen etkinliklerini getirir.
     *
     * @param advisorId Danışman akademisyen ID'si
     * @return Bekleyen etkinlik listesi
     */
    public List<Event> getPendingEventsForAdvisor(UUID advisorId) {
        // 1. Akademisyenin danışmanı olduğu kulüpleri al
        List<UUID> clubIds = clubClient.getClubIdsByAdvisorId(advisorId);

        if (clubIds == null || clubIds.isEmpty()) {
            return List.of(); // Hiçbir kulübün danışmanı değilse boş liste dön
        }

        // 2. Bu kulüplere ait bekleyen etkinlikleri getir
        return eventRepository.findByClubIdInAndStatus(clubIds, EventStatus.PENDING);
    }

    /**
     * Etkinliği Onayla (Danışman Akademisyen İçin)
     * Sadece etkinliğin bağlı olduğu kulübün danışmanı onaylayabilir.
     * RabbitMQ mesajı onay sonrası gönderilir.
     *
     * @param eventId Etkinlik ID'si
     * @param approverId Onaylayan akademisyen ID'si
     * @return Onaylanan etkinlik
     */
    public Event approveEvent(UUID eventId, UUID approverId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı."));

        if (event.getStatus() != EventStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etkinlik bekleyen durumda değil.");
        }

        // Yetki kontrolü: Onaylayan kişi bu kulübün danışmanı mı?
        validateAdvisorAuthorization(event.getClubId(), approverId);

        // Durumu ACTIVE yap
        event.setStatus(EventStatus.ACTIVE);
        Event savedEvent = eventRepository.save(event);
        eventCaches.evictEvent(savedEvent);

        // --- RABBITMQ MESAJI ---
        // Artık etkinlik yayında olduğu için bildirimi şimdi yapıyoruz.
        EventCreatedMessage message = new EventCreatedMessage(
                savedEvent.getId(),
                savedEvent.getTitle(),
                savedEvent.getDescription(),
                savedEvent.getEventTime(),
                savedEvent.getLocation(),
                savedEvent.getClubId(),
                savedEvent.getClubName()
        );

        outboxPublisher.publish(
                EventRabbitMQConfig.CLUB_EXCHANGE_NAME,
                EventRabbitMQConfig.ROUTING_KEY_EVENT_CREATED,
                message
        );

        log.info("Etkinlik onaylandı ve bildirim gönderildi: {} (Onaylayan: {})", savedEvent.getTitle(), approverId);

        return savedEvent;
    }

    /**
     * Etkinliği Reddet (Danışman Akademisyen İçin)
     * Sadece etkinliğin bağlı olduğu kulübün danışmanı reddedebilir.
     *
     * @param eventId Etkinlik ID'si
     * @param rejectorId Reddeden akademisyen ID'si
     * @return Reddedilen etkinlik
     */
    public Event rejectEvent(UUID eventId, UUID rejectorId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı."));

        // Sadece PENDING olanlar reddedilebilir
        if (event.getStatus() != EventStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sadece bekleyen etkinlikler reddedilebilir.");
        }

        // Yetki kontrolü: Reddeden kişi bu kulübün danışmanı mı?
        validateAdvisorAuthorization(event.getClubId(), rejectorId);

        event.setStatus(EventStatus.REJECTED);
        log.info("Etkinlik reddedildi: {} (Reddeden: {})", event.getTitle(), rejectorId);

        Event savedEvent = eventRepository.save(event);
        eventCaches.evictEvent(savedEvent);
        return savedEvent;
    }

    /**
     * Danışman yetki kontrolü.
     * Verilen kişinin, verilen kulübün danışmanı olup olmadığını kontrol eder.
     *
     * @param clubId Kulüp ID'si
     * @param userId Kontrol edilecek kullanıcı ID'si
     * @throws ResponseStatusException Kullanıcı danışman değilse
     */
    private void validateAdvisorAuthorization(UUID clubId, UUID userId) {
        if (!eventAuthorizationService.accessOf(clubId, userId).has(EventAuthorizationService.ADVISE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bu etkinliği onaylama/reddetme yetkiniz yok. Sadece ilgili kulübün danışmanı bu işlemi yapabilir."
            );
        }
    }

    /**
     * Tüm aktif etkinlikleri listeler (Tarihe göre sıralı).
     */
    public List<Event> getAllActiveEvents() {
        return eventRepository.findByStatusOrderByEventTimeAsc(EventStatus.ACTIVE);
    }

    public PageResponse<Event> getActiveEventsPage(int page, Integer size) {
        Page<Event> events = eventRepository.findByStatus(EventStatus.ACTIVE,
                PageResponse.request(page, size, Sort.by("eventTime").and(Sort.by("id"))));
        return PageResponse.of(events, events.getContent());
    }

    public Event getEventDetails(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı."));
    }

    public Event getEventDetailsForViewer(UUID eventId, UUID viewerId) {
        Event event = getEventDetails(eventId);
        if (isPubliclyVisible(event) || eventAuthorizationService.canViewEventInternals(event, viewerId)) {
            return event;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı.");
    }

    private static boolean isPubliclyVisible(Event event) {
        return event.getStatus() == EventStatus.ACTIVE
                || event.getStatus() == EventStatus.COMPLETED
                || event.getStatus() == EventStatus.CANCELLED;
    }

    /**
     * RABBITMQ İÇİN: Bir kulüp silindiğinde o kulübün etkinliklerini iptal et/sil.
     */
    public void deleteEventsByClubId(UUID clubId) {
        List<Event> clubEvents = eventRepository.findByClubId(clubId);

        for (Event event : clubEvents) {
            // Seçenek A: Tamamen silmek
            // if (event.getImageUrl() != null) minioService.deleteFile(event.getImageUrl());
            // eventRepository.delete(event);

            // Seçenek B: İPTAL EDİLDİ olarak işaretlemek (Daha güvenli)
            event.setStatus(EventStatus.CANCELLED);
            eventRepository.save(event);
        }
        eventCaches.evictEvents(clubEvents);

        System.out.println("Cancelled/Deleted " + clubEvents.size() + " events for club: " + clubId);
    }

    /**
     * RABBITMQ İÇİN: Bir kulübün adı değiştiğinde, ona ait tüm etkinliklerdeki
     * kulüp adını da güncelle (Veri tutarlılığı).
     */
    public void updateClubInfoForEvents(UUID clubId, String newClubName) {
        // 1. Bu kulübe ait tüm etkinlikleri bul
        List<Event> clubEvents = eventRepository.findByClubId(clubId);

        if (clubEvents.isEmpty()) return;

        // 2. Hepsini tek tek güncelle
        for (Event event : clubEvents) {
            event.setClubName(newClubName); // Denormalize veriyi güncelle
            // event.setClubLogo(newLogoUrl); // Eğer tutuyorsanız
        }

        // 3. Toplu kaydet
        eventRepository.saveAll(clubEvents);
        eventCaches.evictEvents(clubEvents);

        System.out.println("Updated club name for " + clubEvents.size() + " events.");
    }

    /**
     * QR Kodu okutarak katılımı doğrular (Check-in).
     */
    public boolean verifyTicket(String qrCode, UUID scannerId) {
        // 1. Bileti bul
        EventRegistration registration = eventRegistrationRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new RuntimeException("Invalid ticket (QR Code not found)"));

        Event event = getEventDetails(registration.getEventId());
        eventAuthorizationService.requireEventManager(event, scannerId);
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new IllegalStateException("Event is not active.");
        }

        // 2. Zaten kullanılmış mı?
        if (registration.isAttended()) {
            throw new IllegalStateException("Ticket already used/scanned.");
        }

        // 3. Kullanıldı olarak işaretle
        registration.setAttended(true);
        eventRegistrationRepository.save(registration);
        eventCaches.evictStudentRegistrations(registration.getStudentId());

        return true; // Giriş başarılı
    }

    // Admin Paneli için filtresiz, tüm veriyi (Silinenler hariç her şeyi) getirir
    public List<Event> getAllEventsForAdmin() {
        // findAll() JPA'nın standart metodudur, tüm tabloyu getirir.
        return eventRepository.findAll();
    }

    /**
     * Öğrencinin kayıtlı olduğu tüm etkinlikleri getirir (Cache'li)
     */
    @Cacheable(value = "studentEventRegistrations", key = "#studentId")
    public List<MyEventRegistrationDTO> getStudentEventRegistrations(UUID studentId) {
        List<EventRegistration> registrations = eventRegistrationRepository.findByStudentId(studentId);
        List<UUID> eventIds = registrations.stream().map(EventRegistration::getEventId).distinct().toList();
        Map<UUID, Event> events = eventIds.isEmpty() ? Map.of() : eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        return registrations.stream().map(registration -> {
            Event event = events.get(registration.getEventId());
            if (event == null) return null;

            MyEventRegistrationDTO dto = new MyEventRegistrationDTO();
            dto.setEventId(event.getId());
            dto.setEventTitle(event.getTitle());
            dto.setEventDescription(event.getDescription());
            dto.setEventDate(event.getEventTime());
            dto.setEventLocation(event.getLocation());
            dto.setQrCode(registration.getQrCode());
            dto.setRegistrationTime(registration.getRegistrationTime());
            dto.setAttended(registration.isAttended());
            return dto;
        }).filter(dto -> dto != null).collect(Collectors.toList());
    }

    // ==================== CLUB OFFICIAL DASHBOARD METHODS ====================

    /**
     * Kulüp yetkilisinin oluşturduğu tüm etkinlikleri getirir (Cache'li).
     * @param creatorId Etkinliği oluşturan kulüp yetkilisinin ID'si
     * @return Oluşturulan etkinliklerin listesi
     */
    @Cacheable(value = "clubOfficialCreatedEvents", key = "#creatorId")
    public List<Event> getEventsCreatedByUser(UUID creatorId) {
        return eventRepository.findByCreatedByStudentId(creatorId);
    }

    /**
     * Bir etkinliğe kayıtlı tüm kullanıcıları user-service'den isim/email bilgisiyle birlikte getirir.
     * Yetki kontrolü yapar: Sadece ilgili kulübün yönetim kurulu veya danışmanı erişebilir.
     *
     * @param eventId Etkinlik ID'si
     * @param requesterId İstek yapan kullanıcının ID'si
     * @return Kayıtlı kullanıcıların zenginleştirilmiş listesi
     */
    public List<EventRegistrantDTO> getEventRegistrantsWithUserInfo(UUID eventId, UUID requesterId) {
        // 1. Etkinliği bul
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        // 2. Yetki kontrolü
        eventAuthorizationService.requireEventViewer(event, requesterId);

        // 3. Etkinliğe kayıtlı tüm kullanıcıları getir
        List<EventRegistration> registrations = eventRegistrationRepository.findByEventId(eventId);
        Map<UUID, UserSummary> users = UserLookup.usersById(userClient,
                registrations.stream().map(EventRegistration::getStudentId).toList());

        return registrations.stream().map(registration -> {
            EventRegistrantDTO dto = new EventRegistrantDTO();
            dto.setStudentId(registration.getStudentId());
            dto.setRegistrationTime(registration.getRegistrationTime());
            dto.setAttended(registration.isAttended());

            UserSummary user = users.get(registration.getStudentId());
            if (user != null) {
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setEmail(user.getEmail());
                dto.setDepartment(user.getDepartment());
            } else {
                dto.setFirstName("Bilinmiyor");
                dto.setLastName("");
                dto.setEmail("N/A");
                dto.setDepartment("N/A");
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Bir kulübün etkinliklerini getirir (Kulüp yetkilisi dashboard için).
     * @param clubId Kulüp ID'si
     * @return Kulübün etkinlikleri
     */
    @Cacheable(value = "clubEvents", key = "#clubId")
    public List<Event> getEventsByClubId(UUID clubId) {
        return eventRepository.findByClubId(clubId);
    }

    public List<Event> getEventsByClubIdForManagement(UUID clubId, UUID requesterId) {
        var access = eventAuthorizationService.accessOf(clubId, requesterId);
        if (!access.has(EventAuthorizationService.MANAGE_EVENT_OPERATIONS) && !access.has(EventAuthorizationService.ADVISE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu kulübün etkinlik yönetimine erişim yetkiniz yok.");
        }
        return eventRepository.findByClubId(clubId);
    }
}
