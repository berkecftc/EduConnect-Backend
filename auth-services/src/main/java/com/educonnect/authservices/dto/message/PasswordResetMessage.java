package com.educonnect.authservices.dto.message;

import java.io.Serializable;

/**
 * Şifre sıfırlama e-postası için RabbitMQ mesajı.
 * notification-service tarafından dinlenerek e-posta gönderilir.
 */
public class PasswordResetMessage implements Serializable {

    private String email;
    private String firstName;
    private String lastName;
    private String resetToken;
    private String resetLink;

    public PasswordResetMessage() {
    }

    public PasswordResetMessage(String email, String firstName, String lastName, String resetToken, String resetLink) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.resetToken = resetToken;
        this.resetLink = resetLink;
    }

    // Getter ve Setter metodları
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public String getResetLink() {
        return resetLink;
    }

    public void setResetLink(String resetLink) {
        this.resetLink = resetLink;
    }
}

