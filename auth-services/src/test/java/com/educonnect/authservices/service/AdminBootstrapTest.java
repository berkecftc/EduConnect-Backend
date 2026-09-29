package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    private static final String EMAIL = "root@example.edu";
    private static final String PASSWORD = "Uzun-ve-Guclu-Parola-2026";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, new AuthSecurityProperties(null, null, null, null, null,
                new AuthSecurityProperties.BootstrapAdmin(email, password)));
    }

    @Test
    void notConfigured_shouldDoNothing() {
        bootstrap(null, null).run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void existingAdmin_shouldSkip() {
        when(userRepository.existsByRolesContaining(Role.ROLE_ADMIN)).thenReturn(true);

        bootstrap(EMAIL, PASSWORD).run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void noAdmin_shouldCreateVerifiedAdminWithEncodedPassword() {
        when(userRepository.existsByRolesContaining(Role.ROLE_ADMIN)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");

        bootstrap(EMAIL, PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded");
        assertThat(saved.getValue().getRoles()).isEqualTo(Set.of(Role.ROLE_ADMIN));
        assertThat(saved.getValue().getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void emailTakenByNonAdmin_shouldSkip() {
        when(userRepository.existsByRolesContaining(Role.ROLE_ADMIN)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(new User(EMAIL, "x", Set.of(Role.ROLE_STUDENT))));

        bootstrap(EMAIL, PASSWORD).run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void shortPassword_shouldSkip() {
        when(userRepository.existsByRolesContaining(Role.ROLE_ADMIN)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        bootstrap(EMAIL, "kisa").run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void propertiesToString_shouldNotRevealPassword() {
        assertThat(new AuthSecurityProperties.BootstrapAdmin(EMAIL, PASSWORD).toString()).doesNotContain(PASSWORD);
    }
}
