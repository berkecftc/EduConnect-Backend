package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubSponsorship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubSponsorshipRepository extends JpaRepository<ClubSponsorship, UUID> {

    Optional<ClubSponsorship> findByRequestId(UUID requestId);

    List<ClubSponsorship> findByRequestIdIn(Collection<UUID> requestIds);

    List<ClubSponsorship> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
