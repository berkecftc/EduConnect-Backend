package com.educonnect.eventservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.client.UserLookup;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.dto.response.EventStaffResponse;
import com.educonnect.eventservice.dto.response.UserSummary;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStaff;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.repository.EventStaffRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class EventStaffService {

    private static final Set<EventStatus> STAFFABLE = Set.of(EventStatus.PENDING_PRESIDENT, EventStatus.PENDING, EventStatus.ACTIVE);

    private final EventRepository eventRepository;
    private final EventStaffRepository staffRepository;
    private final EventAuthorizationService authorizationService;
    private final EventSchedule schedule;
    private final EventNotifier notifier;
    private final UserClient userClient;
    private final Clock clock = Clock.systemUTC();

    public EventStaffService(EventRepository eventRepository,
                             EventStaffRepository staffRepository,
                             EventAuthorizationService authorizationService,
                             EventSchedule schedule,
                             EventNotifier notifier,
                             UserClient userClient) {
        this.eventRepository = eventRepository;
        this.staffRepository = staffRepository;
        this.authorizationService = authorizationService;
        this.schedule = schedule;
        this.notifier = notifier;
        this.userClient = userClient;
    }

    public EventStaffResponse propose(UUID eventId, UUID actorId, UUID userId) {
        Event event = staffableEvent(eventId);
        ClubAccess actor = authorizationService.requireAccess(event.getClubId(), actorId,
                EventAuthorizationService.MANAGE_EVENT_OPERATIONS);
        if (!authorizationService.accessOf(event.getClubId(), userId).member()) {
            throw new BadRequestException("STAFF_NOT_MEMBER", "Etkinlik görevlisi kulübün aktif üyelerinden seçilmelidir.");
        }
        if (staffRepository.findByEventIdAndUserId(eventId, userId).isPresent()) {
            throw new ConflictException("STAFF_EXISTS", "Bu üye bu etkinlik için zaten önerildi veya atandı.");
        }
        EventStaff staff = new EventStaff(eventId, userId, actorId, Instant.now(clock));
        if (actor.has(EventAuthorizationService.APPROVE_AS_PRESIDENT)) {
            staff.approve(actorId, Instant.now(clock));
            announceApproval(event, staff);
        } else {
            notifier.notifyLeaders(event, "EVENT_STAFF_REQUEST", event.getClubName() + ": Etkinlik görevlisi onayı",
                    "\"" + event.getTitle() + "\" etkinliği için bir etkinlik görevlisi önerildi; onayınız bekleniyor.");
        }
        return response(staffRepository.save(staff));
    }

    public EventStaffResponse approve(UUID eventId, UUID userId, UUID actorId) {
        Event event = staffableEvent(eventId);
        authorizationService.require(event.getClubId(), actorId, EventAuthorizationService.APPROVE_AS_PRESIDENT);
        EventStaff staff = staff(eventId, userId);
        if (staff.getStatus() == EventStaff.Status.APPROVED) {
            throw new ConflictException("STAFF_ALREADY_APPROVED", "Bu görevli zaten onaylandı.");
        }
        staff.approve(actorId, Instant.now(clock));
        announceApproval(event, staff);
        return response(staffRepository.save(staff));
    }

    public void reject(UUID eventId, UUID userId, UUID actorId) {
        Event event = event(eventId);
        authorizationService.require(event.getClubId(), actorId, EventAuthorizationService.APPROVE_AS_PRESIDENT);
        EventStaff staff = staff(eventId, userId);
        if (staff.getStatus() != EventStaff.Status.PENDING) {
            throw new ConflictException("STAFF_NOT_PENDING", "Yalnız onay bekleyen öneri reddedilebilir.");
        }
        staffRepository.delete(staff);
    }

    public void remove(UUID eventId, UUID userId, UUID actorId) {
        Event event = event(eventId);
        authorizationService.require(event.getClubId(), actorId, EventAuthorizationService.MANAGE_EVENT_OPERATIONS);
        EventStaff staff = staff(eventId, userId);
        staffRepository.delete(staff);
        if (staff.getStatus() == EventStaff.Status.APPROVED) {
            notifier.notify(List.of(userId), NotificationCategory.CLUB_MANAGEMENT, "EVENT_STAFF_REMOVED", event,
                    event.getClubName() + ": Etkinlik görevliliği sona erdi",
                    "\"" + event.getTitle() + "\" etkinliğindeki görevliliğiniz kaldırıldı.");
        }
    }

    @Transactional(readOnly = true)
    public List<EventStaffResponse> list(UUID eventId, UUID viewerId) {
        Event event = event(eventId);
        authorizationService.requireEventViewer(event, viewerId);
        List<EventStaff> staff = staffRepository.findByEventIdOrderByCreatedAtAsc(eventId);
        Map<UUID, UserSummary> users = UserLookup.usersById(userClient, staff.stream().map(EventStaff::getUserId).toList());
        return staff.stream().map(member -> EventStaffResponse.of(member, users.get(member.getUserId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<Event> myAssignments(UUID userId) {
        List<UUID> eventIds = staffRepository.findByUserIdAndStatus(userId, EventStaff.Status.APPROVED).stream()
                .map(EventStaff::getEventId)
                .toList();
        return eventRepository.findAllById(eventIds).stream()
                .filter(event -> event.getStatus() == EventStatus.ACTIVE && event.getEndsAt().isAfter(schedule.now()))
                .toList();
    }

    private void announceApproval(Event event, EventStaff staff) {
        notifier.notify(List.of(staff.getUserId()), NotificationCategory.CLUB_MANAGEMENT, "EVENT_STAFF_ASSIGNED", event,
                event.getClubName() + ": Etkinlik görevlisi oldunuz",
                "\"" + event.getTitle() + "\" etkinliğinde görevli olarak atandınız; girişte biletleri tarayabilirsiniz.");
    }

    private EventStaffResponse response(EventStaff staff) {
        return EventStaffResponse.of(staff, UserLookup.usersById(userClient, List.of(staff.getUserId())).get(staff.getUserId()));
    }

    private Event staffableEvent(UUID eventId) {
        Event event = event(eventId);
        if (!STAFFABLE.contains(event.getStatus()) || !event.getEndsAt().isAfter(schedule.now())) {
            throw new ConflictException("EVENT_NOT_STAFFABLE", "Bitmiş, iptal edilmiş veya reddedilmiş etkinliğe görevli atanamaz.");
        }
        return event;
    }

    private Event event(UUID eventId) {
        Event event = EventFinder.require(eventRepository, eventId);
        if (event.isCampus()) {
            throw new ConflictException("NOT_A_CLUB_EVENT", "Etkinlik görevlisi yalnız kulüp etkinliklerine atanır.");
        }
        return event;
    }

    private EventStaff staff(UUID eventId, UUID userId) {
        return staffRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("STAFF_NOT_FOUND", "Bu etkinlikte böyle bir görevli yok."));
    }
}
