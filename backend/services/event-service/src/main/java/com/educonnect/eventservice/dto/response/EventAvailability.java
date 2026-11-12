package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.AdmissionMode;
import com.educonnect.eventservice.model.EventAudience;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventAvailability(UUID eventId,
                                EventAudience audience,
                                AdmissionMode admission,
                                Integer capacity,
                                long registered,
                                long waitlisted,
                                Integer remaining,
                                boolean registrationOpen,
                                LocalDateTime registrationOpensAt,
                                LocalDateTime registrationClosesAt,
                                LocalDateTime cancelUntil) {
}
