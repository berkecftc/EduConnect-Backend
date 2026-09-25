package com.educonnect.eventservice.service;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.Repository.EventRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
public class EventCaches {

    public static final String STUDENT_EVENT_REGISTRATIONS = "studentEventRegistrations";
    public static final String CLUB_OFFICIAL_CREATED_EVENTS = "clubOfficialCreatedEvents";
    public static final String CLUB_EVENTS = "clubEvents";

    private static final Logger log = LoggerFactory.getLogger(EventCaches.class);

    private final CacheManager cacheManager;
    private final EventRegistrationRepository eventRegistrationRepository;

    public EventCaches(CacheManager cacheManager, EventRegistrationRepository eventRegistrationRepository) {
        this.cacheManager = cacheManager;
        this.eventRegistrationRepository = eventRegistrationRepository;
    }

    public void evictStudentRegistrations(UUID studentId) {
        evict(STUDENT_EVENT_REGISTRATIONS, studentId);
    }

    public void evictUser(UUID userId) {
        evictStudentRegistrations(userId);
        evict(CLUB_OFFICIAL_CREATED_EVENTS, userId);
    }

    public void evictEventListings(Event event) {
        evict(CLUB_EVENTS, event.getClubId());
        evict(CLUB_OFFICIAL_CREATED_EVENTS, event.getCreatedByStudentId());
    }

    public void evictEvent(Event event) {
        evictEventListings(event);
        eventRegistrationRepository.findByEventId(event.getId()).stream()
                .map(EventRegistration::getStudentId)
                .distinct()
                .forEach(this::evictStudentRegistrations);
    }

    public void evictEvents(Collection<Event> events) {
        events.forEach(this::evictEvent);
    }

    private void evict(String cacheName, UUID key) {
        if (key == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (RuntimeException e) {
            log.warn("{} cache temizlenemedi: {}", cacheName, e.getMessage());
        }
    }
}
