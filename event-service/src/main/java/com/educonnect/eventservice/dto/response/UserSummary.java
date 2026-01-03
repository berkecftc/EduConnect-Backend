package com.educonnect.eventservice.dto.response;

import java.util.UUID;

/**
 * User Service'den gelen kullanıcı özet bilgileri.
 */
public class UserSummary {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String department;

    public UserSummary() {}

    public UserSummary(UUID id, String firstName, String lastName, String email, String department) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.department = department;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}
