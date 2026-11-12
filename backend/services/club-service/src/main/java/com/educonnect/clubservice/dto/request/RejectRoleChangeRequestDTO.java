package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Görev değişikliği talebini reddetmek için kullanılan DTO
 */
public class RejectRoleChangeRequestDTO {

    @Size(max = 255, message = "Red nedeni en fazla 255 karakter olabilir")
    private String rejectionReason; // Reddedilme nedeni

    public RejectRoleChangeRequestDTO() {}

    public RejectRoleChangeRequestDTO(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    // --- Getter/Setter ---
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

