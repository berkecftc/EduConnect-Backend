package com.educonnect.eventservice.repository;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    // Bir kulübe ait etkinlikleri getir
    List<Event> findByClubId(UUID clubId);

    // Tarihe göre sıralı, yaklaşan etkinlikleri getir (Ana sayfa için)
    // (Basit versiyonu, detaylısı servis katmanında olacak)

    // --- YENİ METOT ---
    // Belirli bir durumdaki (örn: PENDING) etkinlikleri getir
    List<Event> findByStatus(EventStatus status);

    List<Event> findByStatusOrderByStartsAtAsc(EventStatus status);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    // --- CLUB OFFICIAL DASHBOARD İÇİN ---
    // Kulüp yetkilisinin oluşturduğu etkinlikleri getir
    List<Event> findByCreatedByStudentId(UUID creatorId);

    // Bir kulübe ait ve belirli durumdaki etkinlikleri getir
    List<Event> findByClubIdAndStatus(UUID clubId, EventStatus status);

    // --- AKADEMİSYEN İÇİN ---
    // Birden fazla kulübe ait ve belirli durumdaki etkinlikleri getir
    List<Event> findByClubIdInAndStatus(List<UUID> clubIds, EventStatus status);

    // Birden fazla kulübe ait tüm etkinlikleri getir
    List<Event> findByClubIdIn(List<UUID> clubIds);

    List<Event> findByClubIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(UUID clubId, LocalDateTime from, LocalDateTime to);

    List<Event> findByStatusAndEndsAtBefore(EventStatus status, LocalDateTime moment);

    List<Event> findByStatusInAndStartsAtBefore(Collection<EventStatus> statuses, LocalDateTime moment);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);
}
