package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.response.AttendanceReport;
import com.educonnect.eventservice.service.AttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/manage")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/{eventId}/registrations/{studentId}/check-in")
    public ResponseEntity<Void> checkIn(@PathVariable UUID eventId, @PathVariable UUID studentId,
                                        @RequestHeader("X-Authenticated-User-Id") UUID actorId) {
        attendanceService.checkIn(eventId, studentId, actorId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/registrations/{studentId}/check-in")
    public ResponseEntity<Void> undoCheckIn(@PathVariable UUID eventId, @PathVariable UUID studentId,
                                            @RequestHeader("X-Authenticated-User-Id") UUID actorId) {
        attendanceService.undoCheckIn(eventId, studentId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{eventId}/attendance")
    public ResponseEntity<AttendanceReport> report(@PathVariable UUID eventId,
                                                   @RequestHeader("X-Authenticated-User-Id") UUID viewerId) {
        return ResponseEntity.ok(attendanceService.report(eventId, viewerId));
    }

    @GetMapping("/{eventId}/attendance.csv")
    public ResponseEntity<byte[]> reportCsv(@PathVariable UUID eventId,
                                            @RequestHeader("X-Authenticated-User-Id") UUID viewerId) {
        byte[] body = attendanceService.reportCsv(eventId, viewerId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"katilim-" + eventId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/club/{clubId}/attendance")
    public ResponseEntity<List<AttendanceReport.EventLine>> clubReport(
            @PathVariable UUID clubId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader("X-Authenticated-User-Id") UUID viewerId) {
        return ResponseEntity.ok(attendanceService.clubReport(clubId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), viewerId));
    }
}
