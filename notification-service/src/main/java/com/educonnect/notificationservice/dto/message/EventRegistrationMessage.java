package com.educonnect.notificationservice.dto.message;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

// Bu sınıf event-service'teki ile uyumlu olmalı
public class EventRegistrationMessage implements Serializable {
    private UUID studentId;
    private String eventTitle;
    private LocalDateTime eventTime;
    private String location;
    private String qrCode;

    public EventRegistrationMessage() {}

    // Getter ve Setter metotları (Lombok kullanmıyorsak elle yazıyoruz)
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
}