package com.educonnect.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties.class)
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        Assert.hasText(properties.jwksUri(), "educonnect.security.jwt.jwks-uri must be set");
        Assert.hasText(properties.issuer(), "educonnect.security.jwt.issuer must be set");
        Assert.hasText(properties.audience(), "educonnect.security.jwt.audience must be set");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null
                        && (aud.contains(properties.audience()) || aud.contains(properties.internalAudience())));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audienceValidator));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public VerifiedIdentityFilter verifiedIdentityFilter(JwtDecoder jwtDecoder, JwtProperties properties) {
        return new VerifiedIdentityFilter(jwtDecoder, properties.internalAudience());
    }

    @Bean
    public FilterRegistrationBean<VerifiedIdentityFilter> verifiedIdentityFilterServletRegistration(
            VerifiedIdentityFilter filter) {
        FilterRegistrationBean<VerifiedIdentityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AuthorizationForwardingInterceptor authorizationForwardingInterceptor() {
            return new AuthorizationForwardingInterceptor();
        }
    }
}
