package com.educonnect.userservice.dto.response;

import java.util.UUID;

public class UserSummaryDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String title; // (Akademisyenler için ünvan önemli)

    // Constructor
    public UserSummaryDTO(UUID id, String firstName, String lastName, String title) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.title = title;
    }

    // Getter'lar
    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getTitle() { return title; }

    // Tam ismi göstermek için yardımcı metot (Frontend'de de yapılabilir)
    public String getFullName() {
        return (title != null ? title + " " : "") + firstName + " " + lastName;
    }
}