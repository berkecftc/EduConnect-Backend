package com.educonnect.clubservice.Repository;

import com.educonnect.clubservice.model.ClubMembershipRequest;
import com.educonnect.clubservice.model.MembershipRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubMembershipRequestRepository extends JpaRepository<ClubMembershipRequest, UUID> {

    // Bir kulübün belirli durumdaki isteklerini getir
    List<ClubMembershipRequest> findByClubIdAndStatus(UUID clubId, MembershipRequestStatus status);

    // Bir kulübün tüm isteklerini getir
    List<ClubMembershipRequest> findByClubId(UUID clubId);

    // Bir öğrencinin tüm üyelik isteklerini getir
    List<ClubMembershipRequest> findByStudentId(UUID studentId);

    // Bir öğrencinin belirli bir kulübe olan isteğini bul
    Optional<ClubMembershipRequest> findByClubIdAndStudentId(UUID clubId, UUID studentId);

    // Bir öğrencinin belirli bir kulübe olan bekleyen isteğini bul
    Optional<ClubMembershipRequest> findByClubIdAndStudentIdAndStatus(UUID clubId, UUID studentId, MembershipRequestStatus status);

    // Bir öğrencinin bekleyen isteklerini getir
    List<ClubMembershipRequest> findByStudentIdAndStatus(UUID studentId, MembershipRequestStatus status);

    // Bir kulübün bekleyen isteklerinin sayısı
    long countByClubIdAndStatus(UUID clubId, MembershipRequestStatus status);

    // Bir kulübe belirli bir öğrencinin bekleyen isteği var mı?
    boolean existsByClubIdAndStudentIdAndStatus(UUID clubId, UUID studentId, MembershipRequestStatus status);
}

