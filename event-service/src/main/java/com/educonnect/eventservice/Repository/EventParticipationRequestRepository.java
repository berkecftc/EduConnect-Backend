package com.educonnect.eventservice.Repository;

import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventParticipationRequestRepository extends JpaRepository<EventParticipationRequest, UUID> {

    // Öğrenci bu etkinliğe zaten başvurmuş mu?
    boolean existsByEventIdAndStudentId(UUID eventId, UUID studentId);

    // Öğrencinin belirli bir etkinliğe başvurusunu getir
    Optional<EventParticipationRequest> findByEventIdAndStudentId(UUID eventId, UUID studentId);

    // Bir etkinliğin tüm başvurularını getir
    List<EventParticipationRequest> findByEventId(UUID eventId);

    // Bir etkinliğin belirli durumundaki başvurularını getir
    List<EventParticipationRequest> findByEventIdAndStatus(UUID eventId, ParticipationRequestStatus status);

    // Öğrencinin tüm başvurularını getir
    List<EventParticipationRequest> findByStudentId(UUID studentId);

    // Öğrencinin belirli durumundaki başvurularını getir
    List<EventParticipationRequest> findByStudentIdAndStatus(UUID studentId, ParticipationRequestStatus status);

    // Birden fazla etkinlik için bekleyen başvuruları getir
    List<EventParticipationRequest> findByEventIdInAndStatus(List<UUID> eventIds, ParticipationRequestStatus status);
}

