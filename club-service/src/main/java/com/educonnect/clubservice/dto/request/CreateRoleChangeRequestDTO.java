package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ClubPosition;

/**
 * Görev değişikliği talebi oluşturmak için kullanılan DTO.
 * Hybrid yapı: studentId VEYA studentNumber kullanılabilir.
 * En az biri zorunludur.
 */
public class CreateRoleChangeRequestDTO {

    private String studentId;       // Göreve atanacak öğrenci UUID'si (opsiyonel)
    private String studentNumber;   // Göreve atanacak öğrenci numarası (opsiyonel)
    private ClubPosition requestedRole; // Talep edilen rol

    public CreateRoleChangeRequestDTO() {}

    public CreateRoleChangeRequestDTO(String studentId, ClubPosition requestedRole) {
        this.studentId = studentId;
        this.requestedRole = requestedRole;
    }

    public CreateRoleChangeRequestDTO(String studentId, String studentNumber, ClubPosition requestedRole) {
        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.requestedRole = requestedRole;
    }

    /**
     * En az bir öğrenci tanımlayıcısının (studentId veya studentNumber)
     * sağlanıp sağlanmadığını kontrol eder.
     * @return true eğer en az biri mevcutsa
     */
    public boolean hasStudentIdentifier() {
        return (studentId != null && !studentId.trim().isEmpty())
            || (studentNumber != null && !studentNumber.trim().isEmpty());
    }

    /**
     * studentId alanının dolu olup olmadığını kontrol eder.
     * @return true eğer studentId doluysa
     */
    public boolean hasStudentId() {
        return studentId != null && !studentId.trim().isEmpty();
    }

    /**
     * studentNumber alanının dolu olup olmadığını kontrol eder.
     * @return true eğer studentNumber doluysa
     */
    public boolean hasStudentNumber() {
        return studentNumber != null && !studentNumber.trim().isEmpty();
    }

    // --- Getter/Setter ---
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }

    public ClubPosition getRequestedRole() { return requestedRole; }
    public void setRequestedRole(ClubPosition requestedRole) { this.requestedRole = requestedRole; }
}

