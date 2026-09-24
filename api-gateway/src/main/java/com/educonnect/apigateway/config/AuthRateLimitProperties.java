package com.educonnect.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "educonnect.gateway.auth-rate-limit")
public record AuthRateLimitProperties(boolean enabled,
                                      int loginPerMinute,
                                      int sensitivePerMinute,
                                      int refreshPerMinute) {

    public AuthRateLimitProperties {
        loginPerMinute = loginPerMinute > 0 ? loginPerMinute : 10;
        sensitivePerMinute = sensitivePerMinute > 0 ? sensitivePerMinute : 5;
        refreshPerMinute = refreshPerMinute > 0 ? refreshPerMinute : 30;
    }
}
