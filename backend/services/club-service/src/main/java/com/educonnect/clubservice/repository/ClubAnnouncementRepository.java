package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubAnnouncementRepository extends JpaRepository<ClubAnnouncement, UUID> {

    Optional<ClubAnnouncement> findByRequestId(UUID requestId);

    List<ClubAnnouncement> findByRequestIdIn(Collection<UUID> requestIds);

    Page<ClubAnnouncement> findByClubIdAndPublishedAtIsNotNullAndRemovedAtIsNull(UUID clubId, Pageable pageable);

    long countByClubIdAndPublishedAtGreaterThanEqualAndPublishedAtLessThan(UUID clubId, Instant from, Instant to);
}
