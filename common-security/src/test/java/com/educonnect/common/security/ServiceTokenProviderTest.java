package com.educonnect.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceTokenProviderTest {

    private static final String TOKEN_URI = "http://auth-services/api/auth/internal/token";

    @Test
    void requestsTokenWithClientCredentialsAndCachesIt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        String basic = Base64.getEncoder().encodeToString("event-service:s3cret".getBytes(StandardCharsets.UTF_8));

        server.expect(once(), requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(content().string("grant_type=client_credentials"))
                .andRespond(withSuccess("{\"access_token\":\"first\",\"token_type\":\"Bearer\",\"expires_in\":600}",
                        MediaType.APPLICATION_JSON));

        ServiceTokenProvider provider = new ServiceTokenProvider(builder.build(), "event-service", "s3cret",
                TOKEN_URI, Duration.ofSeconds(60), clock);

        assertThat(provider.getToken()).isEqualTo("first");
        clock.advance(Duration.ofSeconds(500));
        assertThat(provider.getToken()).isEqualTo("first");
        server.verify();
    }

    @Test
    void refreshesTokenBeforeExpiry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"first\",\"expires_in\":600}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"second\",\"expires_in\":600}", MediaType.APPLICATION_JSON));

        ServiceTokenProvider provider = new ServiceTokenProvider(builder.build(), "event-service", "s3cret",
                TOKEN_URI, Duration.ofSeconds(60), clock);

        assertThat(provider.getToken()).isEqualTo("first");
        clock.advance(Duration.ofSeconds(541));
        assertThat(provider.getToken()).isEqualTo("second");
        server.verify();
    }

    @Test
    void failsClearlyWhenSecretIsMissing() {
        ServiceTokenProvider provider = new ServiceTokenProvider(RestClient.create(), "event-service", null,
                TOKEN_URI, Duration.ofSeconds(60), Clock.systemUTC());

        assertThatThrownBy(provider::getToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service-client");
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
