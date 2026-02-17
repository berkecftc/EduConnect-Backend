package com.educonnect.clubservice.Repository;

import com.educonnect.clubservice.model.ClubRole;
import com.educonnect.clubservice.model.RoleChangeRequest;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleChangeRequestRepository extends JpaRepository<RoleChangeRequest, UUID> {

    /**
     * Bir kulübün bekleyen görev değişikliği taleplerini getirir
     */
    List<RoleChangeRequest> findByClubIdAndStatus(UUID clubId, RoleChangeRequestStatus status);

    /**
     * Bir kulübün tüm görev değişikliği taleplerini getirir
     */
    List<RoleChangeRequest> findByClubId(UUID clubId);

    /**
     * Birden fazla kulübün bekleyen taleplerini getirir (danışman birden fazla kulübe bakabilir)
     */
    List<RoleChangeRequest> findByClubIdInAndStatus(List<UUID> clubIds, RoleChangeRequestStatus status);

    /**
     * Bir öğrencinin bekleyen taleplerini getirir
     */
    List<RoleChangeRequest> findByStudentIdAndStatus(UUID studentId, RoleChangeRequestStatus status);

    /**
     * Bir kulüpte belirli bir rol için bekleyen talep var mı kontrol eder
     * (Aynı pozisyona birden fazla talep olmasını engellemek için)
     */
    boolean existsByClubIdAndRequestedRoleAndStatus(UUID clubId, ClubRole requestedRole, RoleChangeRequestStatus status);

    /**
     * Bir öğrenci için belirli bir kulüpte bekleyen talep var mı kontrol eder
     */
    boolean existsByClubIdAndStudentIdAndStatus(UUID clubId, UUID studentId, RoleChangeRequestStatus status);

    /**
     * Belirli bir kulüpte belirli bir rol için bekleyen talebi getirir
     */
    Optional<RoleChangeRequest> findByClubIdAndRequestedRoleAndStatus(UUID clubId, ClubRole requestedRole, RoleChangeRequestStatus status);

    /**
     * Bir kulüpteki bekleyen talep sayısını döndürür
     */
    long countByClubIdAndStatus(UUID clubId, RoleChangeRequestStatus status);
}

