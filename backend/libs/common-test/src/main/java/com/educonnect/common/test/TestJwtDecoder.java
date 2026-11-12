package com.educonnect.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class TestJwtDecoder {

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TestTokens.ISSUER),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null
                        && (aud.contains(TestTokens.AUDIENCE) || aud.contains(TestTokens.INTERNAL_AUDIENCE)))));
        return decoder;
    }
}
