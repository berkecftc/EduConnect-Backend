package com.educonnect.userservice.Repository;

import com.educonnect.userservice.models.ArchivedStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchivedStudentRepository extends JpaRepository<ArchivedStudent, UUID> {

    // Orijinal ID'ye göre arşivlenmiş öğrenciyi bul
    Optional<ArchivedStudent> findByOriginalId(UUID originalId);

    // Tüm arşivlenmiş öğrencileri silme tarihine göre sırala
    List<ArchivedStudent> findAllByOrderByDeletedAtDesc();

    // Bölüme göre arşivlenmiş öğrencileri bul
    List<ArchivedStudent> findByDepartment(String department);
}

