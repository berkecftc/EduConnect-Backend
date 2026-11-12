package com.educonnect.courseservice.dto;

import java.time.Instant;
import java.util.UUID;

public class EnrolledStudentDTO {
    private UUID studentId;
    private String firstName;
    private String lastName;
    private String studentNumber;
    private String email;
    private String department;
    private Instant enrollmentDate;

    public EnrolledStudentDTO() {}

    // Getters and Setters
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Instant getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(Instant enrollmentDate) { this.enrollmentDate = enrollmentDate; }
}
