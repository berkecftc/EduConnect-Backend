package com.educonnect.authservices.dto.message;

import java.io.Serializable;

/**
 * Kullanıcı hesap durumu (onay/red) bildirimi için RabbitMQ mesajı.
 * notification-service tarafından dinlenerek e-posta gönderilir.
 */
public class UserAccountStatusMessage implements Serializable {

    private String email;
    private String firstName;
    private String lastName;
    private String status; // APPROVED veya REJECTED
    private String userType; // STUDENT veya ACADEMICIAN
    private String rejectionReason; // Red durumunda neden (opsiyonel)

    public UserAccountStatusMessage() {}

    public UserAccountStatusMessage(String email, String firstName, String lastName,
                                     String status, String userType, String rejectionReason) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.userType = userType;
        this.rejectionReason = rejectionReason;
    }

    // Getter ve Setter metodları
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

