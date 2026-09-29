package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.AcademicianRequestRepository;
import com.educonnect.authservices.Repository.PasswordResetTokenRepository;
import com.educonnect.authservices.Repository.StudentRequestRepository;
import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplDeleteUserTest {

    private UserRepository userRepository;
    private OutboxPublisher outboxPublisher;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        outboxPublisher = mock(OutboxPublisher.class);
        AcademicianRequestRepository academicianRequests = mock(AcademicianRequestRepository.class);
        StudentRequestRepository studentRequests = mock(StudentRequestRepository.class);
        when(academicianRequests.findByUserId(any())).thenReturn(Optional.empty());
        when(studentRequests.findByEmail(anyString())).thenReturn(Optional.empty());
        service = new AuthServiceImpl(userRepository, academicianRequests, studentRequests,
                mock(PasswordResetTokenRepository.class), mock(PasswordEncoder.class), mock(JWTService.class),
                mock(AuthenticationManager.class), outboxPublisher, mock(RefreshTokenService.class),
                mock(MinioService.class), mock(PasswordPolicy.class), mock(LoginAttemptService.class),
                mock(EmailVerificationService.class), mock(AuthSecurityProperties.class));
    }

    @Test
    void lastAdminCannotBeDeleted() {
        User admin = user(Role.ROLE_ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findAllByRolesContaining(Role.ROLE_ADMIN)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.deleteUser(admin.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).deleteById(any());
        verify(outboxPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void adminCanBeDeletedWhenAnotherAdminRemains() {
        User admin = user(Role.ROLE_ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findAllByRolesContaining(Role.ROLE_ADMIN)).thenReturn(List.of(admin, user(Role.ROLE_ADMIN)));

        service.deleteUser(admin.getId());

        verify(userRepository).deleteById(admin.getId());
    }

    private static User user(Role role) {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", Set.of(role));
        user.setId(UUID.randomUUID());
        return user;
    }
}
