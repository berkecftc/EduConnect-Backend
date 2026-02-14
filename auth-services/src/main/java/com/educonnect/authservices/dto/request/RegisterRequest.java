package com.educonnect.authservices.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RegisterRequest {
    @JsonAlias({"e-mail", "email"})
    private String email;
    private String password;
    @JsonProperty("first_name")
    @JsonAlias({"first_name", "firstName"})
    private String firstName;
    @JsonProperty("last_name")
    @JsonAlias({"last_name", "lastName"})
    private String lastName;
    @JsonProperty("student_id")
    @JsonAlias({"student_id", "studentNumber", "studentId", "student_number"})
    private String studentId;
    private String department;

    private String title; // Örn: "Prof. Dr."
    private String officeNumber; // Örn: "A-101"

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOfficeNumber() { return officeNumber; }
    public void setOfficeNumber(String officeNumber) { this.officeNumber = officeNumber; }

}