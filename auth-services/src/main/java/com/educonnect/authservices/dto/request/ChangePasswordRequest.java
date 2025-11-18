package com.educonnect.authservices.dto.request; // Sizin paket adınız

public class ChangePasswordRequest {

    private String currentPassword;
    private String newPassword;
    private String confirmationPassword;

    // --- Getter ve Setter metotları ---
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmationPassword() { return confirmationPassword; }
    public void setConfirmationPassword(String confirmationPassword) { this.confirmationPassword = confirmationPassword; }
}