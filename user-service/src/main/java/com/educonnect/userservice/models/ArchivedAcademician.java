package com.educonnect.userservice.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "archived_academicians", schema = "user_db")
public class ArchivedAcademician {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID archiveId;

    @Column(nullable = false)
    private UUID originalId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String title;

    private String department;

    private String officeNumber;

    private String profileImageUrl;

    @Column(nullable = false)
    private LocalDateTime deletedAt;

    @Column(length = 1000)
    private String deletionReason;

    public ArchivedAcademician() {
    }

    public ArchivedAcademician(UUID originalId, String firstName, String lastName, String title,
                              String department, String officeNumber, String profileImageUrl,
                              LocalDateTime deletedAt, String deletionReason) {
        this.originalId = originalId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.title = title;
        this.department = department;
        this.officeNumber = officeNumber;
        this.profileImageUrl = profileImageUrl;
        this.deletedAt = deletedAt;
        this.deletionReason = deletionReason;
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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getOfficeNumber() {
        return officeNumber;
    }

    public void setOfficeNumber(String officeNumber) {
        this.officeNumber = officeNumber;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
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
}

