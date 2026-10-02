package com.educonnect.authservices.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "educonnect.auth")
public record AuthSecurityProperties(PasswordPolicy passwordPolicy,
                                     LoginProtection loginProtection,
                                     RefreshTokens refreshTokens,
                                     EmailVerification emailVerification,
                                     Links links,
                                     BootstrapAdmin bootstrapAdmin,
                                     Institution institution) {

    public AuthSecurityProperties {
        passwordPolicy = passwordPolicy != null ? passwordPolicy : new PasswordPolicy(false, 0);
        loginProtection = loginProtection != null ? loginProtection : new LoginProtection(false, 0, null);
        refreshTokens = refreshTokens != null ? refreshTokens : new RefreshTokens(0);
        emailVerification = emailVerification != null ? emailVerification : new EmailVerification(false, null);
        links = links != null ? links : new Links(null, null);
        bootstrapAdmin = bootstrapAdmin != null ? bootstrapAdmin : new BootstrapAdmin(null, null);
        institution = institution != null ? institution : new Institution(null, null, null);
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

    public record EmailVerification(boolean enabled, Duration tokenTtl) {

        public EmailVerification {
            tokenTtl = tokenTtl != null ? tokenTtl : Duration.ofHours(24);
        }
    }

    public record Links(String frontendBaseUrl, String publicApiBaseUrl) {

        public Links {
            frontendBaseUrl = trimTrailingSlash(frontendBaseUrl != null ? frontendBaseUrl : "http://localhost:5173");
            publicApiBaseUrl = trimTrailingSlash(publicApiBaseUrl != null ? publicApiBaseUrl : "http://localhost:8080");
        }

        private static String trimTrailingSlash(String url) {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }

    public record Institution(List<String> studentEmailDomains, List<String> staffEmailDomains, String studentNumberPattern) {

        public Institution {
            studentEmailDomains = normalize(studentEmailDomains);
            staffEmailDomains = normalize(staffEmailDomains);
            studentNumberPattern = studentNumberPattern != null && !studentNumberPattern.isBlank() ? studentNumberPattern : "[0-9]{5,12}";
        }

        private static List<String> normalize(List<String> domains) {
            return domains == null ? List.of() : domains.stream()
                    .filter(domain -> domain != null && !domain.isBlank())
                    .map(domain -> domain.strip().toLowerCase(Locale.ROOT).replaceFirst("^@", ""))
                    .toList();
        }
    }

    public record BootstrapAdmin(String email, String password) {

        public boolean configured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "BootstrapAdmin[email=" + email + ", password=" + (password == null ? "null" : "****") + "]";
        }
    }
}
