package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ClubRole;

/**
 * Görev değişikliği talebi oluşturmak için kullanılan DTO
 */
public class CreateRoleChangeRequestDTO {

    private String studentId; // Göreve atanacak öğrenci ID'si
    private ClubRole requestedRole; // Talep edilen rol

    public CreateRoleChangeRequestDTO() {}

    public CreateRoleChangeRequestDTO(String studentId, ClubRole requestedRole) {
        this.studentId = studentId;
        this.requestedRole = requestedRole;
    }

    // --- Getter/Setter ---
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public ClubRole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(ClubRole requestedRole) { this.requestedRole = requestedRole; }
}

