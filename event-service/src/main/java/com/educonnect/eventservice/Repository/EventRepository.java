package com.educonnect.eventservice.Repository;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
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

    // --- CLUB OFFICIAL DASHBOARD İÇİN ---
    // Kulüp yetkilisinin oluşturduğu etkinlikleri getir
    List<Event> findByCreatedByStudentId(UUID creatorId);

    // Bir kulübe ait ve belirli durumdaki etkinlikleri getir
    List<Event> findByClubIdAndStatus(UUID clubId, EventStatus status);
}