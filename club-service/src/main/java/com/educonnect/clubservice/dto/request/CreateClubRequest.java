package com.educonnect.clubservice.dto.request; // Sizin paket adınız

import java.util.UUID;

// Lombok @Data kullanabilirsiniz
public class CreateClubRequest {
    private String name;
    private String about;
    private UUID academicAdvisorId;
    private UUID clubPresidentId; // Kulübü kuran ve BAŞKAN olarak atanacak öğrencinin ID'si

    // --- Getter/Setter ---
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
    public UUID getClubPresidentId() { return clubPresidentId; }
    public void setClubPresidentId(UUID clubPresidentId) { this.clubPresidentId = clubPresidentId; }
}