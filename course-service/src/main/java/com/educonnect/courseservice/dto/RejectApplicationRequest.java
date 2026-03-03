package com.educonnect.courseservice.dto;

public class RejectApplicationRequest {
    private String rejectionReason;

    public RejectApplicationRequest() {}

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

