package com.educonnect.authservices;

import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
@TestPropertySource(properties = {
        "educonnect.auth.login-protection.enabled=true",
        "educonnect.auth.login-protection.max-attempts=2",
        "educonnect.auth.login-protection.lock-duration=PT15M",
        "educonnect.auth.email-verification.enabled=true"
})
class LoginOutcomeTest {

    private static final String PASSWORD = "Pw-" + UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void eachRefusedLoginCarriesItsOwnErrorCode() throws Exception {
        User pending = user(Role.ROLE_PENDING_ACADEMICIAN, true);
        User suspended = user(Role.ROLE_STUDENT, true);
        suspended.suspend("Test", Instant.now());
        userRepository.save(suspended);
        User unverified = user(Role.ROLE_STUDENT, false);

        login(pending.getEmail(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_PENDING_APPROVAL"));
        login(suspended.getEmail(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_SUSPENDED"));
        login(unverified.getEmail(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
        login(unverified.getEmail(), "yanlis-" + PASSWORD)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLockedAccountSaysWhenToRetry() throws Exception {
        User student = user(Role.ROLE_STUDENT, true);

        login(student.getEmail(), "yanlis-1").andExpect(status().isUnauthorized());
        login(student.getEmail(), "yanlis-2").andExpect(status().isUnauthorized());

        login(student.getEmail(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("LOGIN_LOCKED"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")))
                .andExpect(result -> assertThat(
                        Integer.parseInt(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)),
                        allOf(greaterThan(800), lessThanOrEqualTo(900))));
    }

    private User user(Role role, boolean verified) {
        User user = new User(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.educonnect.local",
                passwordEncoder.encode(PASSWORD), new HashSet<>(Set.of(role)));
        user.setEmailVerifiedAt(verified ? Instant.now() : null);
        return userRepository.save(user);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
