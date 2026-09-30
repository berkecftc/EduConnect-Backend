package com.educonnect.clubservice.model; // Paket adınız

import jakarta.persistence.*;
import com.educonnect.common.storage.ObjectUrlConverter;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "clubs")
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // Kulübün benzersiz ID'si

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String name; // "İlgili kulübün ismi"

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(columnDefinition = "TEXT")
    private String about; // "Hakkında kısmı"

    @Column(name = "logo_url")
    @Convert(converter = ObjectUrlConverter.class)
    private String logoUrl; // "Logosu" (MinIO URL'si)

    @Column(name = "academic_advisor_id")
    private UUID academicAdvisorId; // "Danışman Hocası"nın ID'si (user_db.academicians'a işaret eder)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClubStatus status = ClubStatus.ACTIVE;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_reason", columnDefinition = "TEXT")
    private String closureReason;

    @Column(name = "closed_by")
    private UUID closedBy;

    // Not: Kulüp Başkanı ve YK, 'ClubMembership' tablosunda dinamik olarak yönetilecek.

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.normalizedName = ClubNames.normalize(name);
    }
    public String getNormalizedName() { return normalizedName; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public UUID getAcademicAdvisorId() { return academicAdvisorId; }
    public void setAcademicAdvisorId(UUID academicAdvisorId) { this.academicAdvisorId = academicAdvisorId; }
    public ClubStatus getStatus() { return status; }
    public void setStatus(ClubStatus status) { this.status = status; }
    public boolean isClosed() { return status == ClubStatus.CLOSED; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosureReason() { return closureReason; }
    public UUID getClosedBy() { return closedBy; }

    public void close(UUID closedBy, String reason, Instant closedAt) {
        this.status = ClubStatus.CLOSED;
        this.closedBy = closedBy;
        this.closureReason = reason;
        this.closedAt = closedAt;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
