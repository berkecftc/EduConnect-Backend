package com.educonnect.courseservice.dto;
import java.util.UUID;

public class UserSummaryDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String studentNumber;
    private String department;
    private String title;
    private String academicTitle;
    private String role;
    private String staffCategory;
    private String studentStatus;
    private String staffStatus;

    // Getter & Setter
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAcademicTitle() { return academicTitle; }
    public void setAcademicTitle(String academicTitle) { this.academicTitle = academicTitle; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStaffCategory() { return staffCategory; }
    public void setStaffCategory(String staffCategory) { this.staffCategory = staffCategory; }
    public String getStudentStatus() { return studentStatus; }
    public void setStudentStatus(String studentStatus) { this.studentStatus = studentStatus; }
    public String getStaffStatus() { return staffStatus; }
    public void setStaffStatus(String staffStatus) { this.staffStatus = staffStatus; }
}
