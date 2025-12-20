package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubRole;

import java.util.UUID;

public class MemberDTO {

    private UUID studentId;
    private String firstName;
    private String lastName;
    // 👇 DÜZELTME: İsmi 'clubRole' değil 'role' yaptık ve tipini String yaptık.
    // Böylece frontend 'member.role' dediğinde bunu bulabilecek.
    private String role;

    // Boş Constructor
    public MemberDTO(UUID studentId, ClubRole clubRole) {}

    // Ana Constructor (Service'de kullandığımız)
    public MemberDTO(UUID studentId, String firstName, String lastName, String role) {
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }

    // --- Getter ve Setter ---

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    // 👇 ÖNEMLİ OLAN GETTER BU
    // Frontend JSON'da "role" anahtarını aradığı için metodun adı getRole olmalı.
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}