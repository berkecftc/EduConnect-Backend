package com.educonnect.eventservice.service;

import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.EventCreatedMessage;
import com.educonnect.eventservice.dto.message.EventRegistrationMessage;
import com.educonnect.eventservice.dto.MyEventRegistrationDTO;
import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.dto.response.EventRegistrantDTO;
import com.educonnect.eventservice.dto.response.UserSummary;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.Repository.EventRepository;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.Repository.EventRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final MinioService minioService;
    private final RabbitTemplate rabbitTemplate;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final RestTemplate restTemplate;
    private final UserClient userClient;

    public EventService(EventRepository eventRepository,
                       MinioService minioService,
                       RabbitTemplate rabbitTemplate,
                       EventRegistrationRepository eventRegistrationRepository,
                       RestTemplate restTemplate,
                       UserClient userClient) {
        this.eventRepository = eventRepository;
        this.minioService = minioService;
        this.rabbitTemplate = rabbitTemplate;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.restTemplate = restTemplate;
        this.userClient = userClient;
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

        // 1. KULÜP ID'SİNİ BUL (Servisler Arası Çağrı)
        // "http://SERVİS-ADI/yol" formatını kullanıyoruz
        String clubServiceUrl = "http://CLUB-SERVICE/api/clubs/search?name=" + request.getClubName();

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

        return savedEvent;
    }

    /**
     * 2. YENİ METOT: Bekleyen Etkinlikleri Listele (Admin İçin)
     */
    public List<Event> getPendingEvents() {
        return eventRepository.findByStatus(EventStatus.PENDING);
    }

    /**
     * 3. YENİ METOT: Etkinliği Onayla (Admin İçin)
     * RabbitMQ mesajı ARTIK BURADA gönderiliyor.
     */
    public Event approveEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getStatus() != EventStatus.PENDING) {
            throw new IllegalStateException("Event is not in pending status.");
        }

        // Durumu ACTIVE yap
        event.setStatus(EventStatus.ACTIVE);
        Event savedEvent = eventRepository.save(event);

        // --- RABBITMQ MESAJI (createEvent'ten buraya taşındı) ---
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

        rabbitTemplate.convertAndSend(
                EventRabbitMQConfig.CLUB_EXCHANGE_NAME,
                EventRabbitMQConfig.ROUTING_KEY_EVENT_CREATED,
                message
        );

        System.out.println("Event approved and notification message sent: " + savedEvent.getTitle());
        // -------------------------------------------------------

        return savedEvent;
    }

    /**
     * 4. YENİ METOT: Etkinliği Reddet (Admin İçin)
     */
    public Event rejectEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // Sadece PENDING olanlar reddedilebilir (veya mantığınıza göre değişebilir)
        if (event.getStatus() != EventStatus.PENDING) {
            throw new IllegalStateException("Only pending events can be rejected.");
        }

        event.setStatus(EventStatus.REJECTED);
        // İsterseniz resmi silebilirsiniz:
        // if (event.getImageUrl() != null) minioService.deleteFile(event.getImageUrl());

        return eventRepository.save(event);
    }

    /**
     * Tüm aktif etkinlikleri listeler (Tarihe göre sıralı).
     */
    public List<Event> getAllActiveEvents() {
        // Repository'ye 'findByStatusOrderByEventTimeDesc' metodu eklenebilir
        // Şimdilik basit findAll yapıp filtereliyoruz (Performans için repository metodunu tercih edin)
        return eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.ACTIVE)
                .sorted((e1, e2) -> e1.getEventTime().compareTo(e2.getEventTime()))
                .toList();
    }

    public Event getEventDetails(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
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

        System.out.println("Updated club name for " + clubEvents.size() + " events.");
    }

    /**
     * Öğrenciyi etkinliğe kaydeder ve benzersiz bir QR bilet oluşturur.
     */
    @CacheEvict(value = {"studentEventRegistrations", "eventRegistrants"}, key = "#studentId", allEntries = false)
    public EventRegistration registerForEvent(UUID eventId, UUID studentId) {
        // 1. Etkinlik var mı?
        Event event = getEventDetails(eventId);

        // 2. Etkinlik iptal edilmiş mi?
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cannot register for a cancelled event.");
        }

        // 3. Zaten kayıtlı mı?
        if (eventRegistrationRepository.existsByEventIdAndStudentId(eventId, studentId)) {
            throw new IllegalStateException("Student is already registered for this event.");
        }

        // 4. Kayıt nesnesini hazırla
        EventRegistration registration = new EventRegistration();
        registration.setEventId(eventId);
        registration.setStudentId(studentId);
        registration.setQrCode(UUID.randomUUID().toString());

        // --- HATAYI ÇÖZEN KISIM BURASI ---
        // Kaydetme işlemini yapıp sonucunu 'savedRegistration' değişkenine atıyoruz.
        // Eskiden muhtemelen "return registrationRepository.save(registration);" şeklindeydi.
        EventRegistration savedRegistration = eventRegistrationRepository.save(registration);
        // ----------------------------------

        // 5. RabbitMQ Mesajı Gönder (Artık savedRegistration değişkeni var!)
        EventRegistrationMessage message = new EventRegistrationMessage(
                studentId,
                event.getTitle(),
                event.getEventTime(),
                event.getLocation(),
                savedRegistration.getQrCode() // Burası hata veriyordu
        );

        rabbitTemplate.convertAndSend(
                EventRabbitMQConfig.CLUB_EXCHANGE_NAME,
                EventRabbitMQConfig.ROUTING_KEY_EVENT_REGISTERED,
                message
        );

        System.out.println("Registration notification sent for student: " + studentId);

        // 6. Kaydedilen nesneyi döndür
        return savedRegistration;
    }




    /**
     * QR Kodu okutarak katılımı doğrular (Check-in).
     */
    public boolean verifyTicket(String qrCode) {
        // 1. Bileti bul
        EventRegistration registration = eventRegistrationRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new RuntimeException("Invalid ticket (QR Code not found)"));

        // 2. Zaten kullanılmış mı?
        if (registration.isAttended()) {
            throw new IllegalStateException("Ticket already used/scanned.");
        }

        // 3. Kullanıldı olarak işaretle
        registration.setAttended(true);
        eventRegistrationRepository.save(registration);

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

        return registrations.stream().map(registration -> {
            Event event = eventRepository.findById(registration.getEventId()).orElse(null);
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
     * Yetki kontrolü yapar: Sadece etkinliği oluşturan kişi, ilgili kulübün yetkilisi veya ADMIN erişebilir.
     *
     * @param eventId Etkinlik ID'si
     * @param requesterId İstek yapan kullanıcının ID'si
     * @param isAdmin İstek yapan kullanıcı admin mi?
     * @return Kayıtlı kullanıcıların zenginleştirilmiş listesi
     */
    @Cacheable(value = "eventRegistrants", key = "#eventId")
    public List<EventRegistrantDTO> getEventRegistrantsWithUserInfo(UUID eventId, UUID requesterId, boolean isAdmin) {
        // 1. Etkinliği bul
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        // 2. Yetki kontrolü (Admin değilse)
        if (!isAdmin) {
            checkEventAccessPermission(event, requesterId);
        }

        // 3. Etkinliğe kayıtlı tüm kullanıcıları getir
        List<EventRegistration> registrations = eventRegistrationRepository.findByEventId(eventId);

        // 4. Her kayıt için user-service'den bilgi çek ve DTO'ya dönüştür
        return registrations.stream().map(registration -> {
            EventRegistrantDTO dto = new EventRegistrantDTO();
            dto.setStudentId(registration.getStudentId());
            dto.setRegistrationTime(registration.getRegistrationTime());
            dto.setAttended(registration.isAttended());
            dto.setQrCode(registration.getQrCode());

            // User-service'den kullanıcı bilgilerini çek (graceful fallback ile)
            try {
                UserSummary user = userClient.getUserById(registration.getStudentId());
                if (user != null) {
                    dto.setFirstName(user.getFirstName());
                    dto.setLastName(user.getLastName());
                    dto.setEmail(user.getEmail());
                    dto.setDepartment(user.getDepartment());
                }
            } catch (Exception e) {
                log.warn("User-service'den kullanıcı bilgisi alınamadı (ID: {}): {}",
                        registration.getStudentId(), e.getMessage());
                // Fallback değerler
                dto.setFirstName("Bilinmiyor");
                dto.setLastName("");
                dto.setEmail("N/A");
                dto.setDepartment("N/A");
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Etkinliğe erişim yetkisi kontrolü.
     * Sadece etkinliği oluşturan kişi veya etkinliğin ait olduğu kulübün yetkilisi erişebilir.
     *
     * @param event Etkinlik
     * @param requesterId İstek yapan kullanıcının ID'si
     * @throws ResponseStatusException Yetki yoksa 403 FORBIDDEN
     */
    private void checkEventAccessPermission(Event event, UUID requesterId) {
        // Etkinliği oluşturan kişi mi?
        if (event.getCreatedByStudentId().equals(requesterId)) {
            return; // Erişim izni var
        }

        // Kulüp yetkilisi mi? (Club-service'e çağrı yaparak kontrol)
        // Not: Bu kontrol için club-service'e REST çağrısı yapıyoruz
        try {
            String clubServiceUrl = "http://CLUB-SERVICE/api/clubs/" + event.getClubId() + "/board-members";
            List<Map<String, Object>> boardMembers = restTemplate.getForObject(clubServiceUrl, List.class);

            if (boardMembers != null) {
                boolean isBoardMember = boardMembers.stream()
                        .anyMatch(member -> {
                            Object studentIdObj = member.get("studentId");
                            if (studentIdObj != null) {
                                UUID memberId = UUID.fromString(studentIdObj.toString());
                                return memberId.equals(requesterId);
                            }
                            return false;
                        });

                if (isBoardMember) {
                    return; // Erişim izni var
                }
            }
        } catch (Exception e) {
            log.error("Club-service'den yönetim kurulu bilgisi alınamadı: {}", e.getMessage());
            // Hata durumunda güvenli tarafta kal ve reddet
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Bu etkinliğin kayıtlarını görüntüleme yetkiniz yok");
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

    /**
     * Etkinlik kayıtları cache'ini temizler (yeni kayıt olduğunda çağrılır).
     */
    @CacheEvict(value = "eventRegistrants", key = "#eventId")
    public void evictEventRegistrantsCache(UUID eventId) {
        log.info("Evicted eventRegistrants cache for event: {}", eventId);
    }
}
