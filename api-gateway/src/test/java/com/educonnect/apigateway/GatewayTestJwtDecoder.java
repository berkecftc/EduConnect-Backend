package com.educonnect.apigateway;

import com.educonnect.common.test.TestTokens;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@TestConfiguration(proxyBeanMethods = false)
public class GatewayTestJwtDecoder {

	private static final AtomicReference<Duration> CLOCK_OFFSET = new AtomicReference<>(Duration.ZERO);

	public static void shiftClock(Duration offset) {
		CLOCK_OFFSET.set(offset);
	}

	public static void resetClock() {
		CLOCK_OFFSET.set(Duration.ZERO);
	}

	@Bean
	@Primary
	public ReactiveJwtDecoder testReactiveJwtDecoder() {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(new ShiftedClock());
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtIssuerValidator(TestTokens.ISSUER),
				timestamps,
				new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(TestTokens.AUDIENCE))));
		return decoder;
	}

	private static final class ShiftedClock extends Clock {

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return Instant.now().plus(CLOCK_OFFSET.get());
		}
	}
}
