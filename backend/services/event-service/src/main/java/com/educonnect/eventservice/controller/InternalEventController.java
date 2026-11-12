package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.response.ClubEventStatistics;
import com.educonnect.eventservice.service.ClubEventStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/internal")
public class InternalEventController {

    private final ClubEventStatisticsService statisticsService;

    public InternalEventController(ClubEventStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/clubs/{clubId}/statistics")
    public ResponseEntity<ClubEventStatistics> clubStatistics(
            @PathVariable UUID clubId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(statisticsService.statisticsOf(clubId, from, to));
    }
}
