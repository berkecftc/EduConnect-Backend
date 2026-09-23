package com.educonnect.authservices.service;

import com.educonnect.authservices.config.ServiceClientsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceClientAuthenticatorTest {

    private final ServiceClientAuthenticator authenticator = new ServiceClientAuthenticator(
            new ServiceClientsProperties(Map.of(
                    "event-service", "{bcrypt}" + new BCryptPasswordEncoder().encode("event-secret"),
                    "llm-service", "{noop}llm-secret")));

    @Test
    void acceptsMatchingBcryptSecret() {
        assertThat(authenticator.authenticate("event-service", "event-secret")).isTrue();
    }

    @Test
    void acceptsMatchingNoopSecret() {
        assertThat(authenticator.authenticate("llm-service", "llm-secret")).isTrue();
    }

    @Test
    void rejectsWrongSecret() {
        assertThat(authenticator.authenticate("event-service", "llm-secret")).isFalse();
    }

    @Test
    void rejectsUnknownClient() {
        assertThat(authenticator.authenticate("unknown-service", "event-secret")).isFalse();
    }

    @Test
    void rejectsMissingCredentials() {
        assertThat(authenticator.authenticate(null, "event-secret")).isFalse();
        assertThat(authenticator.authenticate("event-service", null)).isFalse();
    }

    @Test
    void worksWithoutConfiguredClients() {
        ServiceClientAuthenticator empty = new ServiceClientAuthenticator(new ServiceClientsProperties(null));
        assertThat(empty.authenticate("event-service", "event-secret")).isFalse();
    }
}
