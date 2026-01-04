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
    private String studentNumber; // Öğrenci numarası

    public UserSummary() {}

    public UserSummary(UUID id, String firstName, String lastName, String email, String department, String studentNumber) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.department = department;
        this.studentNumber = studentNumber;
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

    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
}
