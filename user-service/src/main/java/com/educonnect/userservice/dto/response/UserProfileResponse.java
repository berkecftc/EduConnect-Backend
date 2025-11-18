package com.educonnect.userservice.dto.response;
import java.io.Serializable;
import java.util.UUID;


public class UserProfileResponse implements Serializable{

    private UUID id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String role; // "Student" veya "Academician"

    private String studentNumber; // Sadece öğrenciyse dolu olacak
    private String title; // Sadece akademisyense dolu olacak
    private String department; // Ortak olabilir

    public UserProfileResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}