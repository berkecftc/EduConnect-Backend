package com.educonnect.userservice.Repository;

import com.educonnect.userservice.models.Academician;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AcademicianRepository extends JpaRepository<Academician, UUID> {

    // Adı VEYA Soyadı, girilen metni (query) içerenleri bul (Case Insensitive)
    List<Academician> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String firstName, String lastName);
}