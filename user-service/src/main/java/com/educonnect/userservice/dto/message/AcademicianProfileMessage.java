package com.educonnect.userservice.dto.message;

import java.io.Serializable;
import java.util.UUID;

// Bu mesaj, 'user-service' tarafından dinlenecek
public class AcademicianProfileMessage implements Serializable {

    private UUID userId; // auth_db'deki User ID
    private String firstName;
    private String lastName;
    private String title;
    private String department;
    private String officeNumber;

    // JSON dönüşümü için boş constructor
    public AcademicianProfileMessage() {}

    public AcademicianProfileMessage(UUID userId, String firstName, String lastName, String title, String department, String officeNumber) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.title = title;
        this.department = department;
        this.officeNumber = officeNumber;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOfficeNumber() { return officeNumber; }
    public void setOfficeNumber(String officeNumber) { this.officeNumber = officeNumber; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
}