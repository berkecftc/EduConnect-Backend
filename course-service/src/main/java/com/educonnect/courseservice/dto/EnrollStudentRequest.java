package com.educonnect.courseservice.dto;

import java.util.UUID;

public class EnrollStudentRequest {
    private UUID studentId;

    public EnrollStudentRequest() {}

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
}

