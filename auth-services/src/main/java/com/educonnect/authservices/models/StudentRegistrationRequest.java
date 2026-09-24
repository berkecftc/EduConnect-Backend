package com.educonnect.authservices.models;

import jakarta.persistence.*;
import com.educonnect.common.storage.ObjectUrlConverter;

@Entity
@Table(name = "student_requests")
@SuppressWarnings("JpaDataSourceORMInspection")
public class StudentRegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password")
    private String password; // Hashlenmiş şifre (onay anına kadar saklanacak)

    @Column(name = "student_number")
    private String studentNumber;

    @Column(name = "department")
    private String department;

    @Column(name = "student_document_url")
    @Convert(converter = ObjectUrlConverter.class)
    private String studentDocumentUrl; // Öğrenci belgesi URL'si (MinIO'da)

    @Column(name = "email_verified_at")
    private java.time.Instant emailVerifiedAt;

    public java.time.Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(java.time.Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    // Getter ve Setter metodları
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void setStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStudentDocumentUrl() {
        return studentDocumentUrl;
    }

    public void setStudentDocumentUrl(String studentDocumentUrl) {
        this.studentDocumentUrl = studentDocumentUrl;
    }
}

