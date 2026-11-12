package com.educonnect.clubservice.dto.request;

/**
 * Kulüp başkanı üyelik isteğini reddederken kullanılacak DTO.
 */
public class RejectMembershipRequestDTO {

    private String rejectionReason; // Red nedeni (opsiyonel)

    public RejectMembershipRequestDTO() {}

    public RejectMembershipRequestDTO(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

