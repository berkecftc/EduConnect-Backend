package com.educonnect.eventservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.config.ApprovalChainSettings;
import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
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
import org.springframework.web.server.ResponseStatusException;

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

    public EventService(EventRepository eventRepository,
                        MinioService minioService,
                        EventRegistrationRepository eventRegistrationRepository,
                        ClubClient clubClient,
                        EventAuthorizationService eventAuthorizationService,
                        EventCaches eventCaches,
                        ApprovalChainSettings approvalChain) {
        this.eventRepository = eventRepository;
        this.minioService = minioService;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.clubClient = clubClient;
        this.eventAuthorizationService = eventAuthorizationService;
        this.eventCaches = eventCaches;
        this.approvalChain = approvalChain;
    }

    public Event createEvent(CreateEventRequest request, MultipartFile posterFile, UUID creatorId) {
        if (posterFile == null || posterFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etkinlik afişi zorunludur.");
        }
        minioService.validateImage(posterFile);

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
        event.setEventTime(request.getEventTime());
        event.setLocation(request.getLocation());
        event.setClubName(request.getClubName());
        event.setCreatedByStudentId(creatorId);
        event.setClubId(resolvedClubId);
        event.setStatus(initialStatus);

        Event savedEvent = eventRepository.save(event);

        log.info("Uploading poster for event: {}", savedEvent.getId());
        String objectName = minioService.uploadFile(posterFile, "events", savedEvent.getId().toString());
        log.info("Poster uploaded successfully. URL: {}", objectName);
        savedEvent.setImageUrl(objectName);
        savedEvent = eventRepository.save(savedEvent);
        log.info("Event saved with imageUrl: {}", savedEvent.getImageUrl());
        eventCaches.evictEventListings(savedEvent);

        return savedEvent;
    }

    public void deleteEventsByClubId(UUID clubId) {
        LocalDateTime now = LocalDateTime.now();
        List<Event> cancelled = eventRepository.findByClubId(clubId).stream()
                .filter(event -> event.getStatus() == EventStatus.PENDING_PRESIDENT
                        || event.getStatus() == EventStatus.PENDING
                        || (event.getStatus() == EventStatus.ACTIVE
                            && (event.getEventTime() == null || event.getEventTime().isAfter(now))))
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

        if (registration.isAttended()) {
            throw new BadRequestException("TICKET_ALREADY_USED", "Ticket already used/scanned.");
        }

        registration.setAttended(true);
        eventRegistrationRepository.save(registration);
        eventCaches.evictStudentRegistrations(registration.getStudentId());

        return true;
    }
}
