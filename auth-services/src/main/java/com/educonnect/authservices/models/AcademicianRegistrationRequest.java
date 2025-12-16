package com.educonnect.authservices.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "academician_requests")
@Data
public class AcademicianRegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User tablosundaki ID ile eşleşecek (Foreign Key mantığı)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // User tablosunda olmayan, Akademisyen tablosuna gidecek veriler:
    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String title;       // Örn: Doç. Dr.
    private String department;  // Örn: Yazılım Müh.

    @Column(name = "office_number")
    private String officeNumber; // Formda varsa

    // İstersen başvuru tarihi
    // private LocalDateTime createdAt;
}