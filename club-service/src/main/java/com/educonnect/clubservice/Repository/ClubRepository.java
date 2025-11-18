package com.educonnect.clubservice.Repository;

import com.educonnect.clubservice.model.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {
    // Kulüp adının benzersizliğini kontrol etmek için
    Optional<Club> findByName(String name);
}