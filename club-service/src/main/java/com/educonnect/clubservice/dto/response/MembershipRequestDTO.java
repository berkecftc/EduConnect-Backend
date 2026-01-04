package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.MembershipRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Üyelik isteği bilgilerini döndürmek için kullanılacak DTO.
 */
public class MembershipRequestDTO {

    private UUID id;
    private UUID clubId;
    private String clubName;
    private String clubLogoUrl;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private MembershipRequestStatus status;
    private LocalDateTime requestDate;
    private LocalDateTime processedDate;
    private String message;
    private String rejectionReason;

    public MembershipRequestDTO() {}

    // Getter/Setter
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public String getClubLogoUrl() { return clubLogoUrl; }
    public void setClubLogoUrl(String clubLogoUrl) { this.clubLogoUrl = clubLogoUrl; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public MembershipRequestStatus getStatus() { return status; }
    public void setStatus(MembershipRequestStatus status) { this.status = status; }

    public LocalDateTime getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDateTime requestDate) { this.requestDate = requestDate; }

    public LocalDateTime getProcessedDate() { return processedDate; }
    public void setProcessedDate(LocalDateTime processedDate) { this.processedDate = processedDate; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

