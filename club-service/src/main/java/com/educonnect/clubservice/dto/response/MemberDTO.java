package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubPosition;

import java.time.Instant;
import java.util.UUID;

public class MemberDTO {

    private UUID studentId;
    private String firstName;
    private String lastName;
    // 👇 DÜZELTME: İsmi 'clubRole' değil 'role' yaptık ve tipini String yaptık.
    // Böylece frontend 'member.role' dediğinde bunu bulabilecek.
    private String role;
    private boolean isActive; // Aktif/pasif durumu
    private Instant termStartDate; // Göreve başlama tarihi
    private Instant termEndDate; // Görev bitiş tarihi

    // Boş Constructor
    public MemberDTO(UUID studentId, ClubPosition clubRole) {
        this.studentId = studentId;
        this.role = clubRole != null ? clubRole.apiName() : null;
    }

    // Ana Constructor (Service'de kullandığımız)
    public MemberDTO(UUID studentId, String firstName, String lastName, String role) {
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.isActive = true; // Varsayılan olarak aktif
    }

    // Tarihli Constructor (Geçmiş başkanlar için)
    public MemberDTO(UUID studentId, String firstName, String lastName, String role,
                     boolean isActive, Instant termStartDate, Instant termEndDate) {
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.isActive = isActive;
        this.termStartDate = termStartDate;
        this.termEndDate = termEndDate;
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

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getTermStartDate() { return termStartDate; }
    public void setTermStartDate(Instant termStartDate) { this.termStartDate = termStartDate; }

    public Instant getTermEndDate() { return termEndDate; }
    public void setTermEndDate(Instant termEndDate) { this.termEndDate = termEndDate; }
}
