package com.educonnect.eventservice.repository;

import com.educonnect.eventservice.model.EventChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventChangeRepository extends JpaRepository<EventChange, UUID> {

    List<EventChange> findByEventIdOrderByCreatedAtDesc(UUID eventId);
}
