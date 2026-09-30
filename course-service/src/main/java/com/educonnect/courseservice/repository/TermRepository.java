package com.educonnect.courseservice.repository;

import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.model.TermSeason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TermRepository extends JpaRepository<Term, UUID> {

    boolean existsByAcademicYearAndSeason(int academicYear, TermSeason season);

    Optional<Term> findByAcademicYearAndSeason(int academicYear, TermSeason season);

    List<Term> findAllByOrderByStartsOnDesc();

    Optional<Term> findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnDesc(LocalDate start, LocalDate end);

    Optional<Term> findFirstByStartsOnGreaterThanOrderByStartsOnAsc(LocalDate day);
}
