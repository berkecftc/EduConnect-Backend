package com.educonnect.clubservice.dto.response;

import java.util.UUID;

/**
 * Akademisyen özet bilgisi (danışman hoca için).
 */
public class AcademicianSummary {
    private UUID id;
    private String firstName;
    private String lastName;
    private String title;
    private String department;
    private String profileImageUrl;

    public AcademicianSummary() {}

    // Getter/Setter
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    // Yardımcı metot - Tam isim (ünvan dahil)
    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(title).append(" ");
        }
        if (firstName != null) {
            sb.append(firstName).append(" ");
        }
        if (lastName != null) {
            sb.append(lastName);
        }
        return sb.toString().trim();
    }
}

