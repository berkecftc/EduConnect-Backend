package com.educonnect.llmservice.dto;

public record ClubResponse(
        String id,
        String name,
        String category,
        String description,
        String meetingDay
) {}