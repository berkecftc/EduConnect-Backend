package com.educonnect.authservices.models;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "academician_requests", schema = "auth_db")
@SuppressWarnings("JpaDataSourceORMInspection")
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

    @Column(name = "id_card_image_url")
    private String idCardImageUrl; // Akademisyen kimlik kartı fotoğrafı URL'si (MinIO'da)

    // İstersen başvuru tarihi
    // private LocalDateTime createdAt;

    // Getter ve Setter metodları
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getOfficeNumber() {
        return officeNumber;
    }

    public void setOfficeNumber(String officeNumber) {
        this.officeNumber = officeNumber;
    }

    public String getIdCardImageUrl() {
        return idCardImageUrl;
    }

    public void setIdCardImageUrl(String idCardImageUrl) {
        this.idCardImageUrl = idCardImageUrl;
    }
}