package com.educonnect.courseservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public class RejectApplicationRequest {
    @Size(max = 255, message = "Red gerekçesi en fazla 255 karakter olabilir")
    @JsonAlias("reason")
    private String rejectionReason;

    public RejectApplicationRequest() {}

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

