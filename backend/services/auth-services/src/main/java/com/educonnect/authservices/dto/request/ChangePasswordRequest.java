package com.educonnect.authservices.dto.request; // Sizin paket adınız

import jakarta.validation.constraints.NotEmpty;

public class ChangePasswordRequest {

    @NotEmpty(message = "Mevcut şifre boş olamaz")
    private String currentPassword;
    @NotEmpty(message = "Yeni şifre boş olamaz")
    private String newPassword;
    @NotEmpty(message = "Şifre tekrarı boş olamaz")
    private String confirmationPassword;

    // --- Getter ve Setter metotları ---
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmationPassword() { return confirmationPassword; }
    public void setConfirmationPassword(String confirmationPassword) { this.confirmationPassword = confirmationPassword; }
}