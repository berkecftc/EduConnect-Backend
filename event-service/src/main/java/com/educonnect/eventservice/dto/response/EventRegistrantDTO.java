package com.educonnect.eventservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Etkinliğe kayıtlı kullanıcı bilgisi DTO.
 * User-service'den çekilen isim/email bilgisiyle zenginleştirilmiş.
 */
public class EventRegistrantDTO {
    private UUID studentId;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private LocalDateTime registrationTime;
    private boolean attended;
    private String qrCode;

    public EventRegistrantDTO() {}

    public EventRegistrantDTO(UUID studentId, String firstName, String lastName, String email,
                              String department, LocalDateTime registrationTime, boolean attended, String qrCode) {
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.department = department;
        this.registrationTime = registrationTime;
        this.attended = attended;
        this.qrCode = qrCode;
    }

    // Getters and Setters
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDateTime getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(LocalDateTime registrationTime) { this.registrationTime = registrationTime; }

    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
}

