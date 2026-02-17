package com.educonnect.clubservice.dto.request;

/**
 * Görev değişikliği talebini reddetmek için kullanılan DTO
 */
public class RejectRoleChangeRequestDTO {

    private String rejectionReason; // Reddedilme nedeni

    public RejectRoleChangeRequestDTO() {}

    public RejectRoleChangeRequestDTO(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    // --- Getter/Setter ---
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

