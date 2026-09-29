package com.educonnect.eventservice.security;

import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class EventAuthorizationService {

    public static final String CREATE_EVENT = "CREATE_EVENT";
    public static final String MANAGE_EVENT_OPERATIONS = "MANAGE_EVENT_OPERATIONS";
    public static final String ADVISE = "ADVISE";

    private static final Logger log = LoggerFactory.getLogger(EventAuthorizationService.class);

    private final ClubClient clubClient;

    public EventAuthorizationService(ClubClient clubClient) {
        this.clubClient = clubClient;
    }

    public ClubAccess accessOf(UUID clubId, UUID userId) {
        try {
            return clubClient.getAccess(clubId, userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı.");
        } catch (FeignException e) {
            log.error("Club access lookup failed: clubId={}, status={}", clubId, e.status());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kulüp yetki bilgisi şu anda alınamıyor.");
        }
    }

    public List<ClubAccess> accessesOf(UUID userId) {
        try {
            List<ClubAccess> accesses = clubClient.getUserAccess(userId);
            return accesses != null ? accesses : List.of();
        } catch (FeignException e) {
            log.error("Club access list lookup failed: status={}", e.status());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kulüp yetki bilgisi şu anda alınamıyor.");
        }
    }

    public void require(UUID clubId, UUID userId, String permission) {
        if (userId == null || !accessOf(clubId, userId).has(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için kulüpte yetkiniz yok.");
        }
    }

    public boolean canManageEvent(Event event, UUID userId) {
        return userId != null && accessOf(event.getClubId(), userId).has(MANAGE_EVENT_OPERATIONS);
    }

    public boolean canViewEventInternals(Event event, UUID userId) {
        if (userId == null) {
            return false;
        }
        ClubAccess access = accessOf(event.getClubId(), userId);
        return access.has(MANAGE_EVENT_OPERATIONS) || access.has(ADVISE);
    }

    public void requireEventManager(Event event, UUID userId) {
        if (!canManageEvent(event, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu etkinliği yönetme yetkiniz yok.");
        }
    }

    public void requireEventViewer(Event event, UUID userId) {
        if (!canViewEventInternals(event, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu etkinliğin yönetim bilgilerini görme yetkiniz yok.");
        }
    }
}
