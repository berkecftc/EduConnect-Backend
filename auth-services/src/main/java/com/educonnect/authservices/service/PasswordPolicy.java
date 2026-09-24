package com.educonnect.authservices.service;

import com.educonnect.authservices.config.AuthSecurityProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class PasswordPolicy {

    static final int LEGACY_RESET_MIN_LENGTH = 6;
    static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456", "1234567", "12345678", "123456789", "1234567890", "12345678910", "111111", "000000",
            "password", "password1", "password123", "passw0rd", "qwerty", "qwerty123", "qwertyuiop",
            "abc123", "abcd1234", "iloveyou", "admin", "admin123", "welcome", "welcome1", "letmein",
            "sifre", "sifre123", "parola", "parola123", "123qwe", "1q2w3e4r", "1q2w3e4r5t", "asdfghjkl",
            "educonnect", "educonnect123", "universite", "ogrenci", "ogrenci123", "galatasaray", "fenerbahce",
            "besiktas", "trabzonspor");

    private final AuthSecurityProperties.PasswordPolicy settings;

    public PasswordPolicy(AuthSecurityProperties properties) {
        this.settings = properties.passwordPolicy();
    }

    public void validateNewPassword(String password, String email) {
        if (!settings.enabled()) {
            requirePresent(password);
            return;
        }
        enforce(password, email);
    }

    public void validateResetPassword(String password, String email) {
        if (!settings.enabled()) {
            requirePresent(password);
            if (password.length() < LEGACY_RESET_MIN_LENGTH) {
                throw new IllegalArgumentException("Şifre en az " + LEGACY_RESET_MIN_LENGTH + " karakter olmalıdır.");
            }
            return;
        }
        enforce(password, email);
    }

    private void enforce(String password, String email) {
        requirePresent(password);
        if (password.length() < settings.minLength()) {
            throw new IllegalArgumentException("Şifre en az " + settings.minLength() + " karakter olmalıdır.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Şifre en fazla " + MAX_LENGTH + " karakter olabilir.");
        }
        if (password.isBlank() || password.chars().distinct().count() < 4) {
            throw new IllegalArgumentException("Şifre çok basit. Farklı karakterler kullanın.");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(normalized)) {
            throw new IllegalArgumentException("Bu şifre çok yaygın. Lütfen başka bir şifre seçin.");
        }
        String localPart = emailLocalPart(email);
        if (localPart != null && localPart.length() >= 4 && normalized.contains(localPart)) {
            throw new IllegalArgumentException("Şifre e-posta adresinizi içeremez.");
        }
    }

    private static void requirePresent(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Şifre boş olamaz.");
        }
    }

    private static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return local.toLowerCase(Locale.ROOT);
    }
}
