package com.educonnect.userservice.Repository;

import com.educonnect.userservice.models.ArchivedAcademician;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchivedAcademicianRepository extends JpaRepository<ArchivedAcademician, UUID> {

    // Orijinal ID'ye göre arşivlenmiş akademisyeni bul
    Optional<ArchivedAcademician> findByOriginalId(UUID originalId);

    // Tüm arşivlenmiş akademisyenleri silme tarihine göre sırala
    List<ArchivedAcademician> findAllByOrderByDeletedAtDesc();

    // Bölüme göre arşivlenmiş akademisyenleri bul
    List<ArchivedAcademician> findByDepartment(String department);
}

