package com.educonnect.gamificationservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TermWindow(String label, LocalDate startsOn, LocalDate endsOn) {
}
