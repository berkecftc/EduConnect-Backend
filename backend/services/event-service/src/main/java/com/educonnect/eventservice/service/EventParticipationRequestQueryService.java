package com.educonnect.eventservice.service;

import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.client.UserLookup;
import com.educonnect.eventservice.dto.response.EventParticipationRequestDTO;
import com.educonnect.eventservice.dto.response.UserSummary;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
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
public class EventParticipationRequestQueryService {

    private final EventParticipationRequestRepository participationRequestRepository;
    private final EventRepository eventRepository;
    private final EventAuthorizationService eventAuthorizationService;
    private final UserClient userClient;

    public EventParticipationRequestQueryService(
            EventParticipationRequestRepository participationRequestRepository,
            EventRepository eventRepository,
            EventAuthorizationService eventAuthorizationService,
            UserClient userClient) {
        this.participationRequestRepository = participationRequestRepository;
        this.eventRepository = eventRepository;
        this.eventAuthorizationService = eventAuthorizationService;
        this.userClient = userClient;
    }

    public List<EventParticipationRequestDTO> getPendingRequestsForEvent(UUID eventId, UUID requesterId) {
        Event event = findManagedEvent(eventId, requesterId);

        List<EventParticipationRequest> requests = participationRequestRepository
                .findByEventIdAndStatus(eventId, ParticipationRequestStatus.PENDING);

        return enrichRequestsWithUserInfo(requests, event);
    }

    public List<EventParticipationRequestDTO> getAllRequestsForEvent(UUID eventId, UUID requesterId) {
        Event event = findManagedEvent(eventId, requesterId);

        List<EventParticipationRequest> requests = participationRequestRepository.findByEventId(eventId);

        return enrichRequestsWithUserInfo(requests, event);
    }

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

    private Event findManagedEvent(UUID eventId, UUID requesterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı"));

        if (!eventAuthorizationService.canManageEvent(event, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu etkinliğin isteklerini görüntüleme yetkiniz yok");
        }
        return event;
    }

    private void applyStudentInfo(EventParticipationRequestDTO dto, UserSummary user) {
        if (user != null) {
            dto.setStudentName(user.getFirstName() + " " + user.getLastName());
            dto.setStudentEmail(user.getEmail());
        } else {
            dto.setStudentName("Bilinmiyor");
            dto.setStudentEmail("N/A");
        }
    }

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
