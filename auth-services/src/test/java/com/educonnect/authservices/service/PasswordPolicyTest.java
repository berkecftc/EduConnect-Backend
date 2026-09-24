package com.educonnect.authservices.service;

import com.educonnect.authservices.config.AuthSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static PasswordPolicy policy(boolean enabled) {
        return new PasswordPolicy(new AuthSecurityProperties(
                new AuthSecurityProperties.PasswordPolicy(enabled, 10), null, null, null, null, null));
    }

    @Test
    void disabled_shouldKeepLegacyBehaviour() {
        PasswordPolicy legacy = policy(false);

        assertThatCode(() -> legacy.validateNewPassword("abc", "ayse@example.edu")).doesNotThrowAnyException();
        assertThatCode(() -> legacy.validateResetPassword("abcdef", "ayse@example.edu")).doesNotThrowAnyException();
        assertThatThrownBy(() -> legacy.validateResetPassword("abcde", "ayse@example.edu"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> legacy.validateNewPassword("", "ayse@example.edu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_shouldRejectShortPasswords() {
        assertThatThrownBy(() -> policy(true).validateNewPassword("Kisa1234", "ayse@example.edu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    void enabled_shouldRejectCommonPasswords() {
        assertThatThrownBy(() -> policy(true).validateNewPassword("1234567890", "ayse@example.edu"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy(true).validateNewPassword("Password123", "ayse@example.edu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_shouldRejectPasswordsContainingEmailName() {
        assertThatThrownBy(() -> policy(true).validateNewPassword("ayseyilmaz2024!", "ayseyilmaz@example.edu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_shouldRejectRepeatedCharacters() {
        assertThatThrownBy(() -> policy(true).validateNewPassword("aaaaaaaaaaaa", "ayse@example.edu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_shouldApplySameRulesToReset() {
        assertThatThrownBy(() -> policy(true).validateResetPassword("abcdef", "ayse@example.edu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_shouldAcceptStrongPasswords() {
        assertThatCode(() -> policy(true).validateNewPassword("Kampus-Yolu-42", "ayse@example.edu"))
                .doesNotThrowAnyException();
    }
}
