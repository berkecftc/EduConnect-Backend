package com.educonnect.authservices.listener;

import com.educonnect.authservices.Repository.ClubManagementSyncRepository;
import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.dto.message.ClubManagementStatusChangedEvent;
import com.educonnect.authservices.models.ClubManagementSync;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubManagementStatusListenerTest {

    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-24T10:00:00Z");

    private UserRepository userRepository;
    private ClubManagementSyncRepository syncRepository;
    private ClubManagementStatusListener listener;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        syncRepository = mock(ClubManagementSyncRepository.class);
        listener = new ClubManagementStatusListener(userRepository, syncRepository);

        user = new User();
        user.setId(userId);
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_STUDENT, Role.ROLE_PENDING_CLUB_OFFICIAL)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(syncRepository.findById(userId)).thenReturn(Optional.empty());
    }

    private ClubManagementStatusChangedEvent event(boolean managesClub, Instant at) {
        return new ClubManagementStatusChangedEvent(UUID.randomUUID(), ClubManagementStatusChangedEvent.EVENT_TYPE,
                1, at, userId, managesClub);
    }

    @Test
    void grantsClubOfficialAndClearsPendingRequest() {
        listener.handle(event(true, now));

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.ROLE_STUDENT, Role.ROLE_CLUB_OFFICIAL);
        verify(syncRepository).save(any(ClubManagementSync.class));
    }

    @Test
    void revokesClubOfficialWhenNoLongerManaging() {
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_STUDENT, Role.ROLE_CLUB_OFFICIAL)));

        listener.handle(event(false, now));

        assertThat(user.getRoles()).containsExactly(Role.ROLE_STUDENT);
    }

    @Test
    void ignoresOlderOrDuplicateEvents() {
        ClubManagementSync sync = new ClubManagementSync(userId, now, UUID.randomUUID());
        when(syncRepository.findById(userId)).thenReturn(Optional.of(sync));

        listener.handle(event(true, now.minusSeconds(5)));
        listener.handle(event(true, now));

        verify(userRepository, never()).save(any());
        assertThat(user.getRoles()).doesNotContain(Role.ROLE_CLUB_OFFICIAL);
    }

    @Test
    void appliesNewerEventAndMovesWatermark() {
        UUID oldEvent = UUID.randomUUID();
        ClubManagementSync sync = new ClubManagementSync(userId, now.minusSeconds(60), oldEvent);
        when(syncRepository.findById(userId)).thenReturn(Optional.of(sync));

        ClubManagementStatusChangedEvent newer = event(true, now);
        listener.handle(newer);

        assertThat(sync.getLastEventAt()).isEqualTo(now);
        assertThat(sync.getLastEventId()).isEqualTo(newer.eventId());
        assertThat(user.getRoles()).contains(Role.ROLE_CLUB_OFFICIAL);
    }

    @Test
    void ignoresDeletedUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        listener.handle(event(true, now));

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsUnsupportedVersionsWithoutRequeue() {
        ClubManagementStatusChangedEvent v2 = new ClubManagementStatusChangedEvent(UUID.randomUUID(),
                ClubManagementStatusChangedEvent.EVENT_TYPE, 2, now, userId, true);
        assertThatThrownBy(() -> listener.handle(v2)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
