package com.educonnect.eventservice.repository;

import com.educonnect.eventservice.model.EventStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventStaffRepository extends JpaRepository<EventStaff, UUID> {

    List<EventStaff> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    Optional<EventStaff> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserIdAndStatus(UUID eventId, UUID userId, EventStaff.Status status);

    List<EventStaff> findByUserIdAndStatus(UUID userId, EventStaff.Status status);
}
