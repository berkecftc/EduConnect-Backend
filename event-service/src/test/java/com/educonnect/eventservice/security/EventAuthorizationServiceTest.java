package com.educonnect.eventservice.security;

import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventAuthorizationServiceTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private ClubClient clubClient;
    private EventAuthorizationService service;
    private Event event;

    @BeforeEach
    void setUp() {
        clubClient = mock(ClubClient.class);
        service = new EventAuthorizationService(clubClient);
        event = new Event();
        event.setClubId(clubId);
    }

    private void givenPermissions(String... permissions) {
        when(clubClient.getAccess(clubId, userId))
                .thenReturn(new ClubAccess(clubId, userId, null, true, false, false, Set.of(permissions)));
    }

    private static FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://club-service", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("ClubClient#getAccess", feign.Response.builder()
                .status(status).reason("x").request(request).headers(Map.of()).build());
    }

    @Test
    void boardMemberCanManageEventButCannotCreateOne() {
        givenPermissions(EventAuthorizationService.MANAGE_EVENT_OPERATIONS);

        assertThat(service.canManageEvent(event, userId)).isTrue();
        assertThatThrownBy(() -> service.require(clubId, userId, EventAuthorizationService.CREATE_EVENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void advisorCanViewButNotManage() {
        givenPermissions(EventAuthorizationService.ADVISE);

        assertThat(service.canViewEventInternals(event, userId)).isTrue();
        assertThat(service.canManageEvent(event, userId)).isFalse();
    }

    @Test
    void anonymousUserHasNoAccess() {
        assertThat(service.canViewEventInternals(event, null)).isFalse();
        assertThat(service.canManageEvent(event, null)).isFalse();
    }

    @Test
    void failsClosedWhenClubServiceIsUnavailable() {
        when(clubClient.getAccess(clubId, userId)).thenThrow(feignError(500));

        assertThatThrownBy(() -> service.canManageEvent(event, userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void unknownClubIsNotFound() {
        when(clubClient.getAccess(clubId, userId)).thenThrow(feignError(404));

        assertThatThrownBy(() -> service.accessOf(clubId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
