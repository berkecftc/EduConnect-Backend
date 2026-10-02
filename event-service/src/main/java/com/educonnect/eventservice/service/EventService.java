package com.educonnect.eventservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.config.ApprovalChainSettings;
import com.educonnect.eventservice.dto.request.CampusEventRequest;
import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.AdmissionMode;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventAudience;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final MinioService minioService;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final ClubClient clubClient;
    private final EventAuthorizationService eventAuthorizationService;
    private final EventCaches eventCaches;
    private final ApprovalChainSettings approvalChain;
    private final EventSchedule schedule;

    public EventService(EventRepository eventRepository,
                        MinioService minioService,
                        EventRegistrationRepository eventRegistrationRepository,
                        ClubClient clubClient,
                        EventAuthorizationService eventAuthorizationService,
                        EventCaches eventCaches,
                        ApprovalChainSettings approvalChain,
                        EventSchedule schedule) {
        this.eventRepository = eventRepository;
        this.minioService = minioService;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.clubClient = clubClient;
        this.eventAuthorizationService = eventAuthorizationService;
        this.eventCaches = eventCaches;
        this.approvalChain = approvalChain;
        this.schedule = schedule;
    }

    public Event createCampusEvent(CampusEventRequest request, MultipartFile posterFile, UUID creatorId) {
        boolean hasPoster = posterFile != null && !posterFile.isEmpty();
        if (hasPoster) {
            minioService.validateImage(posterFile);
        }
        LocalDateTime endsAt = schedule.endOrDefault(request.startsAt(), request.endsAt());
        schedule.requireValidNewSchedule(request.startsAt(), endsAt);
        schedule.requireValidRegistration(request.startsAt(), request.registrationOpensAt(), request.registrationClosesAt(),
                request.cancelUntil());

        Event event = new Event();
        event.setTitle(request.title().strip());
        event.setDescription(request.description());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(endsAt);
        event.setLocation(request.location());
        event.setSpeakers(request.speakers() == null || request.speakers().isBlank() ? null : request.speakers().strip());
        event.setOrganizerName(request.organizerName().strip());
        event.setAudience(EventAudience.CAMPUS);
        event.setAdmission(request.admission() != null ? request.admission() : AdmissionMode.AUTO_CONFIRM);
        event.setCapacity(request.capacity());
        event.setRegistrationOpensAt(request.registrationOpensAt());
        event.setRegistrationClosesAt(request.registrationClosesAt());
        event.setCancelUntil(request.cancelUntil());
        event.setCreatedByStudentId(creatorId);
        event.setStatus(EventStatus.ACTIVE);
        event.setPublishedAt(LocalDateTime.now());
        Event saved = eventRepository.save(event);
        if (hasPoster) {
            saved.setImageUrl(minioService.uploadFile(posterFile, "events", saved.getId().toString()));
            saved = eventRepository.save(saved);
        }
        eventCaches.evictEventListings(saved);
        log.info("Campus event published: eventId={}, by={}", saved.getId(), creatorId);
        return saved;
    }

    public List<Event> campusEvents() {
        return eventRepository.findByClubIdIsNullOrderByStartsAtDesc();
    }

    public Event createEvent(CreateEventRequest request, MultipartFile posterFile, UUID creatorId) {
        boolean hasPoster = posterFile != null && !posterFile.isEmpty();
        if (hasPoster) {
            minioService.validateImage(posterFile);
        }
        LocalDateTime endsAt = schedule.endOrDefault(request.getStartsAt(), request.getEndsAt());
        schedule.requireValidNewSchedule(request.getStartsAt(), endsAt);
        schedule.requireValidRegistration(request.getStartsAt(), request.getRegistrationOpensAt(), request.getRegistrationClosesAt(),
                request.getCancelUntil());
        if (request.getAudience() == EventAudience.CAMPUS) {
            throw new BadRequestException("AUDIENCE_NOT_ALLOWED", "Kulüp etkinliği yalnız üyelere veya tüm öğrencilere açılabilir.");
        }

        UUID resolvedClubId;
        try {
            resolvedClubId = clubClient.getClubIdByName(request.getClubName());
        } catch (FeignException.FeignClientException e) {
            throw new IllegalArgumentException("Invalid club name: " + request.getClubName() + ". Club not found.");
        } catch (FeignException e) {
            log.warn("Club lookup by name failed: status={}", e.status());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                    "Kulüp bilgisi şu anda alınamıyor. Lütfen daha sonra tekrar deneyin.");
        }
        if (resolvedClubId == null) {
            throw new IllegalArgumentException("Invalid club name: " + request.getClubName() + ". Club not found.");
        }

        EventStatus initialStatus;
        if (approvalChain.enabled()) {
            ClubAccess access = eventAuthorizationService.requireAccess(resolvedClubId, creatorId,
                    EventAuthorizationService.PREPARE_EVENT);
            initialStatus = access.has(EventAuthorizationService.APPROVE_AS_PRESIDENT)
                    ? EventStatus.PENDING
                    : EventStatus.PENDING_PRESIDENT;
        } else {
            eventAuthorizationService.require(resolvedClubId, creatorId, EventAuthorizationService.CREATE_EVENT);
            initialStatus = EventStatus.PENDING;
        }

        Event event = new Event();
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setStartsAt(request.getStartsAt());
        event.setEndsAt(endsAt);
        event.setSpeakers(request.getSpeakers() == null || request.getSpeakers().isBlank() ? null : request.getSpeakers().strip());
        event.setAudience(request.getAudience() != null ? request.getAudience() : EventAudience.MEMBERS_ONLY);
        event.setAdmission(request.getAdmission() != null ? request.getAdmission() : AdmissionMode.APPROVAL_REQUIRED);
        event.setCapacity(request.getCapacity());
        event.setRegistrationOpensAt(request.getRegistrationOpensAt());
        event.setRegistrationClosesAt(request.getRegistrationClosesAt());
        event.setCancelUntil(request.getCancelUntil());
        event.setLocation(request.getLocation());
        event.setClubName(request.getClubName());
        event.setCreatedByStudentId(creatorId);
        event.setClubId(resolvedClubId);
        event.setStatus(initialStatus);

        Event savedEvent = eventRepository.save(event);

        if (hasPoster) {
            String objectName = minioService.uploadFile(posterFile, "events", savedEvent.getId().toString());
            savedEvent.setImageUrl(objectName);
            savedEvent = eventRepository.save(savedEvent);
        }
        eventCaches.evictEventListings(savedEvent);

        return savedEvent;
    }

    public void deleteEventsByClubId(UUID clubId) {
        LocalDateTime now = LocalDateTime.now();
        List<Event> cancelled = eventRepository.findByClubId(clubId).stream()
                .filter(event -> event.getStatus() == EventStatus.PENDING_PRESIDENT
                        || event.getStatus() == EventStatus.PENDING
                        || (event.getStatus() == EventStatus.ACTIVE
                            && event.getEndsAt().isAfter(now)))
                .toList();
        cancelled.forEach(event -> event.setStatus(EventStatus.CANCELLED));
        eventRepository.saveAll(cancelled);
        eventCaches.evictEvents(cancelled);
        log.info("Kapanan kulübün {} gelecek etkinliği iptal edildi: clubId={}", cancelled.size(), clubId);
    }

    public void updateClubInfoForEvents(UUID clubId, String newClubName) {
        List<Event> clubEvents = eventRepository.findByClubId(clubId);

        if (clubEvents.isEmpty()) return;

        for (Event event : clubEvents) {
            event.setClubName(newClubName);
        }

        eventRepository.saveAll(clubEvents);
        eventCaches.evictEvents(clubEvents);

        log.info("Updated club name for {} events.", clubEvents.size());
    }

    public boolean verifyTicket(String qrCode, UUID scannerId) {
        EventRegistration registration = eventRegistrationRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new BadRequestException("INVALID_TICKET", "Invalid ticket (QR Code not found)"));

        Event event = EventFinder.require(eventRepository, registration.getEventId());
        eventAuthorizationService.requireEventManager(event, scannerId);
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new BadRequestException("EVENT_NOT_ACTIVE", "Event is not active.");
        }
        schedule.requireCheckInOpen(event);

        if (!registration.isActive()) {
            throw new BadRequestException("TICKET_CANCELLED", "Bu kayıt iptal edilmiş.");
        }
        if (registration.isAttended()) {
            throw new BadRequestException("TICKET_ALREADY_USED", "Ticket already used/scanned.");
        }

        registration.setAttended(true);
        eventRegistrationRepository.save(registration);
        eventCaches.evictStudentRegistrations(registration.getStudentId());

        return true;
    }
}
