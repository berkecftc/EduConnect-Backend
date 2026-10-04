package com.educonnect.eventservice.service;

import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.client.UserLookup;
import com.educonnect.eventservice.dto.MyEventRegistrationDTO;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.dto.response.EventRegistrantDTO;
import com.educonnect.eventservice.dto.response.PageResponse;
import com.educonnect.eventservice.dto.response.UserSummary;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventQueryService {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final UserClient userClient;
    private final EventAuthorizationService eventAuthorizationService;

    public EventQueryService(EventRepository eventRepository,
                             EventRegistrationRepository eventRegistrationRepository,
                             UserClient userClient,
                             EventAuthorizationService eventAuthorizationService) {
        this.eventRepository = eventRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.userClient = userClient;
        this.eventAuthorizationService = eventAuthorizationService;
    }

    public List<Event> getAllActiveEvents() {
        return eventRepository.findByStatusOrderByStartsAtAsc(EventStatus.ACTIVE);
    }

    public PageResponse<Event> getActiveEventsPage(int page, Integer size) {
        Page<Event> events = eventRepository.findByStatus(EventStatus.ACTIVE,
                PageResponse.request(page, size, Sort.by("startsAt").and(Sort.by("id"))));
        return PageResponse.of(events, events.getContent());
    }

    public Event getEventDetails(UUID eventId) {
        return EventFinder.require(eventRepository, eventId);
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

    public List<Event> getAllEventsForAdmin() {
        return eventRepository.findAll();
    }

    @Cacheable(value = EventCaches.STUDENT_EVENT_REGISTRATIONS, key = "#studentId")
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
            dto.setEventDate(event.getStartsAt());
            dto.setEventLocation(event.getLocation());
            dto.setQrCode(registration.getQrCode());
            dto.setRegistrationTime(registration.getRegistrationTime());
            dto.setAttended(registration.isAttended());
            dto.setRegistrationStatus(registration.getStatus().name());
            dto.setEventStatus(event.getStatus().name());
            return dto;
        }).filter(dto -> dto != null).collect(Collectors.toList());
    }

    public List<Event> getEventsOfManagedClubs(UUID userId) {
        List<UUID> clubIds = eventAuthorizationService.accessesOf(userId).stream()
                .filter(access -> access.has(EventAuthorizationService.MANAGE_EVENT_OPERATIONS))
                .map(ClubAccess::clubId)
                .distinct()
                .toList();
        return clubIds.isEmpty() ? List.of() : eventRepository.findByClubIdIn(clubIds);
    }

    public List<EventRegistrantDTO> getEventRegistrantsWithUserInfo(UUID eventId, UUID requesterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        eventAuthorizationService.requireEventViewer(event, requesterId);

        List<EventRegistration> registrations = eventRegistrationRepository.findByEventId(eventId);
        Map<UUID, UserSummary> users = UserLookup.usersById(userClient,
                registrations.stream().map(EventRegistration::getStudentId).toList());

        return registrations.stream().map(registration -> {
            EventRegistrantDTO dto = new EventRegistrantDTO();
            dto.setStudentId(registration.getStudentId());
            dto.setRegistrationTime(registration.getRegistrationTime());
            dto.setAttended(registration.isAttended());
            dto.setStatus(registration.getStatus().name());

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

    @Cacheable(value = EventCaches.CLUB_EVENTS, key = "#clubId")
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
