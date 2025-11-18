package com.educonnect.userservice.dto.message;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public class UserRegisteredMessage implements Serializable {

    private UUID userId;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    // Ogrenciye ozel ek alanlar
    private String studentNumber;
    private String department;

    public UserRegisteredMessage() {}

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}