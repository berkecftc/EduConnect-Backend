package com.educonnect.clubservice.model; // Paket adınız

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "clubs", schema = "club_db")
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // Kulübün benzersiz ID'si

    @Column(nullable = false, unique = true)
    private String name; // "İlgili kulübün ismi"

    @Column(columnDefinition = "TEXT")
    private String about; // "Hakkında kısmı"

    @Column(name = "logo_url")
    private String logoUrl; // "Logosu" (MinIO URL'si)

    @Column(name = "academic_advisor_id", nullable = false)
    private UUID academicAdvisorId; // "Danışman Hocası"nın ID'si (user_db.academicians'a işaret eder)

    // Not: Kulüp Başkanı ve YK, 'ClubMembership' tablosunda dinamik olarak yönetilecek.

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
}