package com.educonnect.authservices.dto.message;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

// RabbitMQ mesajları için Serializable implementasyonu iyi bir pratiktir.
public class UserRegisteredMessage implements Serializable {

    private UUID userId;
    private String firstName;
    private String lastName;
    private String email; // Kullanıcının e-posta adresi
    private Set<String> roles;
    // Opsiyonel: Öğrenci profili için ekstra alanlar
    private String studentNumber; // JSON tarafında student_id ile eşlenecek
    private String department;


    public UserRegisteredMessage() {}

    public UserRegisteredMessage(UUID userId, String firstName, String lastName, Set<String> roles) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
    }

    // Opsiyonel alanları da alan aşırı yüklenmiş kurucu
    public UserRegisteredMessage(UUID userId, String firstName, String lastName, String email, Set<String> roles,
                                 String studentNumber, String department) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.roles = roles;
        this.studentNumber = studentNumber;
        this.department = department;
    }


    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}