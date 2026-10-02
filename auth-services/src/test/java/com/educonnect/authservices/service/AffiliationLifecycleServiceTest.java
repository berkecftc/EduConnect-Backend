package com.educonnect.authservices.service;

import com.educonnect.authservices.dto.request.AffiliationStatusRequest;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.AffiliationStatusChangeRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliationLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAdministrationService administration = mock(UserAdministrationService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private AffiliationLifecycleService service(int graceDays) {
        return new AffiliationLifecycleService(userRepository, mock(AffiliationStatusChangeRepository.class),
                mock(OutboxPublisher.class), administration, mock(AcademicianAssignmentGuard.class), refreshTokenService,
                graceDays, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aGracePeriodDelaysTheClosureAndSignsTheUserOut() {
        User user = new User("mezun@ogr.uni.edu.tr", "hash", new HashSet<>(Set.of(Role.ROLE_STUDENT)));
        user.setId(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service(30).changeStudentStatus(user.getId(), new AffiliationStatusRequest("GRADUATED", null, null), "admin");

        assertThat(user.getClosureDueAt()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
        assertThat(user.getRoles()).isEmpty();
        verify(refreshTokenService).revokeAllSessions(user.getId());
        verify(administration, never()).deleteUser(eq(user.getId()), anyString());
    }

    @Test
    void dueAccountsCloseUnlessAnAffiliationCameBack() {
        User closing = new User("a@x", "hash", new HashSet<>());
        closing.setId(UUID.randomUUID());
        User rejoined = new User("b@x", "hash", new HashSet<>(Set.of(Role.ROLE_ACADEMICIAN)));
        rejoined.setId(UUID.randomUUID());
        rejoined.setClosureDueAt(NOW.minusSeconds(60));
        when(userRepository.findByClosureDueAtBefore(NOW)).thenReturn(List.of(closing, rejoined));

        service(30).closeDueAccounts();

        verify(administration).deleteUser(closing.getId(), "Hesap kapanış süresi doldu");
        verify(administration, never()).deleteUser(eq(rejoined.getId()), anyString());
        assertThat(rejoined.getClosureDueAt()).isNull();
    }
}
