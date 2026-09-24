package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.RefreshTokenRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.models.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private final UUID userId = UUID.randomUUID();

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private JWTService jwtService;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        AuthSecurityProperties properties = new AuthSecurityProperties(null, null, new AuthSecurityProperties.RefreshTokens(2));
        service = new RefreshTokenService(repository, jwtService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issue_shouldStoreOnlyHashOfReturnedToken() {
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(60_000L);
        when(repository.findActiveByUserId(userId, NOW)).thenReturn(List.of());

        String raw = service.issue(userId);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getExpiryDate()).isEqualTo(NOW.plusMillis(60_000L));
    }

    @Test
    void issue_whenActiveSessionsExceedLimit_shouldRevokeOldestFamilies() {
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(60_000L);
        RefreshToken newest = token(UUID.randomUUID(), NOW.plusSeconds(60));
        RefreshToken middle = token(UUID.randomUUID(), NOW.plusSeconds(60));
        RefreshToken oldest = token(UUID.randomUUID(), NOW.plusSeconds(60));
        when(repository.findActiveByUserId(userId, NOW)).thenReturn(List.of(newest, middle, oldest));

        service.issue(userId);

        verify(repository).revokeFamily(oldest.getFamilyId(), NOW);
        verify(repository, never()).revokeFamily(newest.getFamilyId(), NOW);
        verify(repository, never()).revokeFamily(middle.getFamilyId(), NOW);
    }

    @Test
    void rotate_withValidToken_shouldRevokeItAndIssueNewTokenInSameFamily() {
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(60_000L);
        UUID familyId = UUID.randomUUID();
        RefreshToken current = token(familyId, NOW.plusSeconds(60));
        when(repository.findByTokenHashForUpdate(OpaqueTokens.hash("raw-token"))).thenReturn(Optional.of(current));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate("raw-token");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.rawToken()).isNotEqualTo("raw-token");
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(familyId);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(rotated.rawToken()));
    }

    @Test
    void rotate_withAlreadyRotatedToken_shouldRevokeWholeFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken reused = token(familyId, NOW.plusSeconds(60));
        reused.revoke(NOW.minusSeconds(5));
        when(repository.findByTokenHashForUpdate(OpaqueTokens.hash("stolen"))).thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> service.rotate("stolen"))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class);
        verify(repository).revokeFamily(familyId, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_withExpiredToken_shouldFail() {
        RefreshToken expired = token(UUID.randomUUID(), NOW.minusSeconds(1));
        when(repository.findByTokenHashForUpdate(OpaqueTokens.hash("old"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("old"))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_withUnknownToken_shouldFail() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown"))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class);
    }

    @Test
    void revokeAllSessions_shouldRevokeEveryActiveTokenOfUser() {
        service.revokeAllSessions(userId);

        verify(repository).revokeAllForUser(eq(userId), eq(NOW));
    }

    private RefreshToken token(UUID familyId, Instant expiry) {
        return new RefreshToken(OpaqueTokens.hash(UUID.randomUUID().toString()), familyId, userId, expiry);
    }
}
