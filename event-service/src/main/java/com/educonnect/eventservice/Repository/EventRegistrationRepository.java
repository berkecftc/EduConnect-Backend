package com.educonnect.eventservice.Repository;

import com.educonnect.eventservice.model.EventRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

    // Öğrenci bu etkinliğe zaten kayıtlı mı?
    boolean existsByEventIdAndStudentId(UUID eventId, UUID studentId);

    // QR kod doğrulama için
    Optional<EventRegistration> findByQrCode(String qrCode);
}