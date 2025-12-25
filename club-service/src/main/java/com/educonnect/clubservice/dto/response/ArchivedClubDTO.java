package com.educonnect.clubservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public class ArchivedClubDTO {

    private UUID archiveId;
    private UUID originalId;
    private String name;
    private String about;
    private String logoUrl;
    private UUID academicAdvisorId;
    private LocalDateTime deletedAt;
    private String deletionReason;
    private UUID deletedByAdminId;

    public ArchivedClubDTO() {
    }

    public ArchivedClubDTO(UUID archiveId, UUID originalId, String name, String about,
                          String logoUrl, UUID academicAdvisorId, LocalDateTime deletedAt,
                          String deletionReason, UUID deletedByAdminId) {
        this.archiveId = archiveId;
        this.originalId = originalId;
        this.name = name;
        this.about = about;
        this.logoUrl = logoUrl;
        this.academicAdvisorId = academicAdvisorId;
        this.deletedAt = deletedAt;
        this.deletionReason = deletionReason;
        this.deletedByAdminId = deletedByAdminId;
    }

    // Getters and Setters
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

