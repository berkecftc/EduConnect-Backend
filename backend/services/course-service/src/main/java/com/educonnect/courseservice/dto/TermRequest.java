package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.TermSeason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TermRequest(@NotNull @Min(2000) @Max(2200) Integer academicYear,
                          @NotNull TermSeason season,
                          @NotNull LocalDate startsOn,
                          @NotNull LocalDate endsOn,
                          LocalDate enrollmentOpensOn,
                          LocalDate enrollmentClosesOn) {
}
