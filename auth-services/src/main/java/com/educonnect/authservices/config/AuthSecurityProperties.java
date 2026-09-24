package com.educonnect.authservices.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "educonnect.auth")
public record AuthSecurityProperties(PasswordPolicy passwordPolicy,
                                     LoginProtection loginProtection,
                                     RefreshTokens refreshTokens) {

    public AuthSecurityProperties {
        passwordPolicy = passwordPolicy != null ? passwordPolicy : new PasswordPolicy(false, 0);
        loginProtection = loginProtection != null ? loginProtection : new LoginProtection(false, 0, null);
        refreshTokens = refreshTokens != null ? refreshTokens : new RefreshTokens(0);
    }

    public record PasswordPolicy(boolean enabled, int minLength) {

        public PasswordPolicy {
            minLength = minLength > 0 ? minLength : 10;
        }
    }

    public record LoginProtection(boolean enabled, int maxAttempts, Duration lockDuration) {

        public LoginProtection {
            maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
            lockDuration = lockDuration != null ? lockDuration : Duration.ofMinutes(15);
        }
    }

    public record RefreshTokens(int maxActivePerUser) {

        public RefreshTokens {
            maxActivePerUser = maxActivePerUser > 0 ? maxActivePerUser : 10;
        }
    }
}
