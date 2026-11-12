package com.educonnect.eventservice.dto.message; // notification-service'te paketi değiştirin

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class EventRegistrationMessage implements Serializable {
    public static final String ORIGIN_SELF = "SELF";
    public static final String ORIGIN_REQUEST_APPROVED = "REQUEST_APPROVED";
    public static final String ORIGIN_WAITLIST_PROMOTED = "WAITLIST_PROMOTED";

    private UUID studentId;
    private String eventTitle;
    private LocalDateTime eventTime;
    private String location;
    private String qrCode; // Biletin kodu
    private String origin;

    // Boş Constructor
    public EventRegistrationMessage() {}

    public EventRegistrationMessage(UUID studentId, String eventTitle, LocalDateTime eventTime, String location, String qrCode,
                                    String origin) {
        this.studentId = studentId;
        this.eventTitle = eventTitle;
        this.eventTime = eventTime;
        this.location = location;
        this.qrCode = qrCode;
        this.origin = origin;
    }

    // Getter ve Setter'lar...
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
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
}
