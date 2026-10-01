package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.model.TermSeason;

import java.time.LocalDate;
import java.util.UUID;

public record TermResponse(UUID id,
                           int academicYear,
                           TermSeason season,
                           String label,
                           LocalDate startsOn,
                           LocalDate endsOn,
                           LocalDate enrollmentOpensOn,
                           LocalDate enrollmentClosesOn) {

    public static TermResponse of(Term term) {
        return new TermResponse(term.getId(), term.getAcademicYear(), term.getSeason(), term.label(), term.getStartsOn(),
                term.getEndsOn(), term.getEnrollmentOpensOn(), term.getEnrollmentClosesOn());
    }
}
