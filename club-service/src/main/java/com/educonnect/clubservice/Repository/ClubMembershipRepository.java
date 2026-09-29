package com.educonnect.clubservice.Repository;

import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition; // Enum'u import et
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubMembershipRepository extends JpaRepository<ClubMembership, UUID> {

    // Bir kulübün tüm üyelerini/yönetimini getir
    List<ClubMembership> findByClubId(UUID clubId);

    List<ClubMembership> findByClubIdIn(Collection<UUID> clubIds);

    // Bir öğrencinin tüm kulüp üyeliklerini getir
    List<ClubMembership> findByStudentId(UUID studentId);

    // Bir öğrencinin belirli bir kulüpteki rolünü bul
    Optional<ClubMembership> findByClubIdAndStudentId(UUID clubId, UUID studentId);

    // Bir öğrencinin belirli bir kulüpte üye olup olmadığını kontrol et
    boolean existsByClubIdAndStudentId(UUID clubId, UUID studentId);

    // Bir öğrencinin belirli bir kulüpte aktif üye olup olmadığını kontrol et
    boolean existsByClubIdAndStudentIdAndIsActive(UUID clubId, UUID studentId, boolean isActive);

    // Bir kulübün başkanını bulmak için (veya belirli bir roldekileri)
    List<ClubMembership> findByClubIdAndClubRole(UUID clubId, ClubPosition role);

    // Bir kulübün aktif başkanını bulmak için
    List<ClubMembership> findByClubIdAndClubRoleAndIsActive(UUID clubId, ClubPosition role, boolean isActive);

    // Bir kulübün geçmiş başkanlarını tarih sırasıyla getirmek için
    List<ClubMembership> findByClubIdAndClubRoleAndIsActiveOrderByTermStartDateDesc(UUID clubId, ClubPosition role, boolean isActive);

    // Bir kulübün toplam üye sayısını getir
    long countByClubId(UUID clubId);

    @Query("select m.clubId as clubId, count(m) as total from ClubMembership m where m.clubId in :clubIds group by m.clubId")
    List<ClubMemberCount> countByClubIds(@Param("clubIds") Collection<UUID> clubIds);

    interface ClubMemberCount {
        UUID getClubId();
        long getTotal();
    }

    // Bir kulübün aktif üye sayısını getir
    long countByClubIdAndIsActive(UUID clubId, boolean isActive);

    // Bir öğrencinin herhangi bir kulüpte belirli bir rolde aktif olup olmadığını kontrol et
    // (Tek başkanlık kuralı için kullanılır)
    boolean existsByStudentIdAndClubRoleAndIsActive(UUID studentId, ClubPosition role, boolean isActive);

    // Bir öğrencinin belirli bir rolde aktif olduğu üyeliği getir
    List<ClubMembership> findByStudentIdAndClubRoleAndIsActive(UUID studentId, ClubPosition role, boolean isActive);
}