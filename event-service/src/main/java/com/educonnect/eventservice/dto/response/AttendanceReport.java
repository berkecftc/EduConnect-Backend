package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.CheckInMethod;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.RegistrationStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AttendanceReport(UUID eventId,
                               String title,
                               EventStatus status,
                               LocalDateTime startsAt,
                               long registered,
                               long attended,
                               long noShow,
                               long cancelled,
                               List<Row> rows) {

    public record Row(UUID studentId,
                      String firstName,
                      String lastName,
                      String studentNumber,
                      RegistrationStatus status,
                      boolean attended,
                      Instant checkedInAt,
                      UUID checkedInBy,
                      CheckInMethod method) {
    }

    public record EventLine(UUID eventId,
                            String title,
                            LocalDateTime startsAt,
                            EventStatus status,
                            long registered,
                            long attended,
                            long noShow) {
    }
}
