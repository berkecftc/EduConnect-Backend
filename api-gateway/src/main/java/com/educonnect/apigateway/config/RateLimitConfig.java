package com.educonnect.apigateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthRateLimitProperties.class, GeneralRateLimitProperties.class})
public class RateLimitConfig {
}
