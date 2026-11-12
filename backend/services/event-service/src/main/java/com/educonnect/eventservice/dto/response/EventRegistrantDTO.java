package com.educonnect.eventservice.dto.response;

import java.time.Instant;
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
    private Instant registrationTime;
    private boolean attended;
    private String qrCode;
    private String status;

    public EventRegistrantDTO() {}

    public EventRegistrantDTO(UUID studentId, String firstName, String lastName, String email,
                              String department, Instant registrationTime, boolean attended, String qrCode) {
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

    public Instant getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(Instant registrationTime) { this.registrationTime = registrationTime; }

    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
