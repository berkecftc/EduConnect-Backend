package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "archived_clubs", schema = "club_db")
public class ArchivedClub {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID archiveId;

    @Column(nullable = false)
    private UUID originalId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "academic_advisor_id")
    private UUID academicAdvisorId;

    @Column(nullable = false)
    private LocalDateTime deletedAt;

    @Column(length = 1000)
    private String deletionReason;

    @Column(name = "deleted_by_admin_id")
    private UUID deletedByAdminId;

    public ArchivedClub() {
    }

    public ArchivedClub(UUID originalId, String name, String about, String logoUrl,
                       UUID academicAdvisorId, LocalDateTime deletedAt,
                       String deletionReason, UUID deletedByAdminId) {
        this.originalId = originalId;
        this.name = name;
        this.about = about;
        this.logoUrl = logoUrl;
        this.academicAdvisorId = academicAdvisorId;
        this.deletedAt = deletedAt;
        this.deletionReason = deletionReason;
        this.deletedByAdminId = deletedByAdminId;
    }

    public UUID getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(UUID archiveId) {
        this.archiveId = archiveId;
    }

    public UUID getOriginalId() {
        return originalId;
    }

    public void setOriginalId(UUID originalId) {
        this.originalId = originalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public UUID getAcademicAdvisorId() {
        return academicAdvisorId;
    }

    public void setAcademicAdvisorId(UUID academicAdvisorId) {
        this.academicAdvisorId = academicAdvisorId;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletionReason() {
        return deletionReason;
    }

    public void setDeletionReason(String deletionReason) {
        this.deletionReason = deletionReason;
    }

    public UUID getDeletedByAdminId() {
        return deletedByAdminId;
    }

    public void setDeletedByAdminId(UUID deletedByAdminId) {
        this.deletedByAdminId = deletedByAdminId;
    }
}

