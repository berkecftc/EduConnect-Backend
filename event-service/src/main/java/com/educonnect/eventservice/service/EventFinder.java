package com.educonnect.eventservice.service;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

final class EventFinder {

    private EventFinder() {
    }

    static Event require(EventRepository eventRepository, UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etkinlik bulunamadı."));
    }
}
