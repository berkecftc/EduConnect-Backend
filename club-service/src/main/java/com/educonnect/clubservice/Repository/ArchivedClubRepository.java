package com.educonnect.clubservice.Repository;

import com.educonnect.clubservice.model.ArchivedClub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchivedClubRepository extends JpaRepository<ArchivedClub, UUID> {

    // Orijinal ID'ye göre arşivlenmiş kulübü bul
    Optional<ArchivedClub> findByOriginalId(UUID originalId);

    // Tüm arşivlenmiş kulüpleri silme tarihine göre sırala
    List<ArchivedClub> findAllByOrderByDeletedAtDesc();

    // İsme göre arşivlenmiş kulüpleri bul
    List<ArchivedClub> findByNameContainingIgnoreCase(String name);
}

