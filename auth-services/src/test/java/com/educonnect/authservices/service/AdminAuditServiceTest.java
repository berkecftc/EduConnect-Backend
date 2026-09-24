package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.AdminAuditRepository;
import com.educonnect.authservices.models.AdminAuditEntry;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Mock
    private AdminAuditRepository repository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AdminAuditService service() {
        return new AdminAuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void record_shouldStoreAuthenticatedAdminAsActor() {
        User admin = new User("admin@example.edu", "x", Set.of(Role.ROLE_ADMIN));
        admin.setId(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        UUID target = UUID.randomUUID();

        service().record("SUSPEND_USER", "USER", target, "Kural ihlali");

        ArgumentCaptor<AdminAuditEntry> saved = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getActorId()).isEqualTo(admin.getId());
        assertThat(saved.getValue().getActorEmail()).isEqualTo("admin@example.edu");
        assertThat(saved.getValue().getAction()).isEqualTo("SUSPEND_USER");
        assertThat(saved.getValue().getTargetId()).isEqualTo(target.toString());
        assertThat(saved.getValue().getDetails()).isEqualTo("Kural ihlali");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void record_shouldTruncateLongDetails() {
        service().record("REJECT_STUDENT", "STUDENT_REQUEST", 42L, "x".repeat(5000));

        ArgumentCaptor<AdminAuditEntry> saved = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDetails()).hasSize(AdminAuditService.MAX_DETAILS_LENGTH);
        assertThat(saved.getValue().getActorId()).isNull();
    }

    @Test
    void list_shouldClampPageSize() {
        when(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, AdminAuditService.MAX_PAGE_SIZE)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service().list(-3, 10_000).size()).isEqualTo(AdminAuditService.MAX_PAGE_SIZE);
    }
}
