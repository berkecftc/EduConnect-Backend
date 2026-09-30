package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {

    Optional<Club> findByNormalizedNameAndStatusNot(String normalizedName, ClubStatus status);

    boolean existsByNormalizedNameAndStatusNot(String normalizedName, ClubStatus status);

    List<Club> findByStatusNot(ClubStatus status);

    List<Club> findByStatusNot(ClubStatus status, Sort sort);

    Page<Club> findByStatusNot(ClubStatus status, Pageable pageable);

    List<Club> findByAcademicAdvisorId(UUID academicAdvisorId);
}
