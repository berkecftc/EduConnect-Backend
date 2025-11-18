package com.educonnect.clubservice.Repository;

import com.educonnect.clubservice.model.ClubCreationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClubCreationRequestRepository extends JpaRepository<ClubCreationRequest, UUID> {

    /**
     * Admin Paneli'nin bekleyen (PENDING) veya onaylanmış (APPROVED) talepleri
     * listelemesi için kullanılır.
     * @param status Aranacak talep durumu (PENDING, APPROVED, REJECTED)
     * @return Belirtilen duruma sahip taleplerin listesi
     */
    List<ClubCreationRequest> findByStatus(String status);
}