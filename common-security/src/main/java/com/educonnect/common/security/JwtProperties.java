package com.educonnect.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "educonnect.security.jwt")
public record JwtProperties(String jwksUri, String issuer, String audience) {
}
