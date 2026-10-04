package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCategory;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {

    Optional<Club> findByNormalizedNameAndStatusNot(String normalizedName, ClubStatus status);

    boolean existsByNormalizedNameAndStatusNot(String normalizedName, ClubStatus status);

    List<Club> findByStatusNot(ClubStatus status);

    List<Club> findByStatusNot(ClubStatus status, Sort sort);

    List<Club> findByStatus(ClubStatus status, Sort sort);

    Page<Club> findByStatusNot(ClubStatus status, Pageable pageable);

    List<Club> findByStatusNotAndProfileCategory(ClubStatus status, ClubCategory category);

    Page<Club> findByStatusNotAndProfileCategory(ClubStatus status, ClubCategory category, Pageable pageable);

    List<Club> findByAcademicAdvisorId(UUID academicAdvisorId);

    @Query("""
            select count(c) > 0 from Club c
            where c.status <> com.educonnect.clubservice.model.ClubStatus.CLOSED
              and (c.academicAdvisorId = :viewerId
                   or exists (select m.id from ClubMembership m where m.clubId = c.id and m.studentId = :viewerId
                              and m.isActive = true and m.clubRole in :managementRoles))
              and (exists (select s.id from ClubMembership s where s.clubId = c.id and s.studentId = :studentId and s.isActive = true)
                   or exists (select r.id from ClubMembershipRequest r where r.clubId = c.id and r.studentId = :studentId
                              and r.status = com.educonnect.clubservice.model.MembershipRequestStatus.PENDING))
            """)
    boolean managesStudent(@Param("viewerId") UUID viewerId, @Param("studentId") UUID studentId,
                           @Param("managementRoles") Collection<ClubPosition> managementRoles);
}
