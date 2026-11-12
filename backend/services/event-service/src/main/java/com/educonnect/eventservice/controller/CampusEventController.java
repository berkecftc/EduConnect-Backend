package com.educonnect.eventservice.controller;

import com.educonnect.common.web.BadRequestException;
import com.educonnect.eventservice.dto.request.CampusEventRequest;
import com.educonnect.eventservice.dto.response.EventResponse;
import com.educonnect.eventservice.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/campus")
@PreAuthorize("hasAuthority('PERM_CAMPUS_PUBLISHER') or hasRole('ADMIN')")
public class CampusEventController {

    private final EventService eventService;

    public CampusEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> create(@Valid @RequestPart("data") CampusEventRequest request,
                                                @RequestPart(value = "poster", required = false) MultipartFile poster,
                                                @RequestHeader("X-Authenticated-User-Id") UUID publisherId) {
        String contentType = poster == null || poster.isEmpty() ? "image/" : poster.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("INVALID_POSTER_TYPE", "Afiş yalnızca resim dosyası olabilir.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(eventService.createCampusEvent(request, poster, publisherId)));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> list() {
        return ResponseEntity.ok(EventResponse.from(eventService.campusEvents()));
    }
}
