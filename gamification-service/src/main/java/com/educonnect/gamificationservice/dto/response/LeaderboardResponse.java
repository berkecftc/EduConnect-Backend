package com.educonnect.gamificationservice.dto.response;

import com.educonnect.gamificationservice.service.LeaderboardPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LeaderboardResponse(
        LeaderboardPeriod period,
        String termLabel,
        LocalDate from,
        LocalDate to,
        UUID facultyId,
        List<LeaderboardEntryResponse> entries,
        Standing me
) {

    public record Standing(Integer rank, long points, boolean visible) {
    }
}
