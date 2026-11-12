package com.educonnect.courseservice.service;

import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.courseservice.dto.TermRequest;
import com.educonnect.courseservice.dto.TermResponse;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TermService {

    private final TermRepository termRepository;
    private final CourseRepository courseRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public TermService(TermRepository termRepository, CourseRepository courseRepository) {
        this.termRepository = termRepository;
        this.courseRepository = courseRepository;
    }

    public TermResponse create(TermRequest request) {
        validate(request);
        if (termRepository.existsByAcademicYearAndSeason(request.academicYear(), request.season())) {
            throw new ConflictException("TERM_EXISTS", "Bu akademik yıl ve dönem zaten tanımlı.");
        }
        Term term = new Term(request.academicYear(), request.season());
        term.schedule(request.startsOn(), request.endsOn(), request.enrollmentOpensOn(), request.enrollmentClosesOn());
        return TermResponse.of(termRepository.save(term));
    }

    public TermResponse update(UUID termId, TermRequest request) {
        validate(request);
        Term term = find(termId);
        boolean renamed = term.getAcademicYear() != request.academicYear() || term.getSeason() != request.season();
        if (renamed) {
            if (courseRepository.existsByTermId(termId)) {
                throw new ConflictException("TERM_IN_USE", "Ders açılmış dönemin yılı ve türü değiştirilemez.");
            }
            termRepository.findByAcademicYearAndSeason(request.academicYear(), request.season())
                    .filter(other -> !other.getId().equals(termId))
                    .ifPresent(other -> {
                        throw new ConflictException("TERM_EXISTS", "Bu akademik yıl ve dönem zaten tanımlı.");
                    });
            term.rename(request.academicYear(), request.season());
        }
        term.schedule(request.startsOn(), request.endsOn(), request.enrollmentOpensOn(), request.enrollmentClosesOn());
        return TermResponse.of(termRepository.save(term));
    }

    public void delete(UUID termId) {
        Term term = find(termId);
        if (courseRepository.existsByTermId(termId)) {
            throw new ConflictException("TERM_IN_USE", "Ders açılmış dönem silinemez.");
        }
        termRepository.delete(term);
    }

    @Transactional(readOnly = true)
    public List<TermResponse> terms() {
        return termRepository.findAllByOrderByStartsOnDesc().stream().map(TermResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public TermResponse currentTerm() {
        return TermResponse.of(current());
    }

    @Transactional(readOnly = true)
    public Term current() {
        LocalDate today = LocalDate.now(clock);
        return termRepository.findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnDesc(today, today)
                .or(() -> termRepository.findFirstByStartsOnGreaterThanOrderByStartsOnAsc(today))
                .orElseThrow(() -> new ConflictException("NO_TERM", "Tanımlı güncel veya gelecek bir dönem yok."));
    }

    @Transactional(readOnly = true)
    public Term find(UUID termId) {
        return termRepository.findById(termId)
                .orElseThrow(() -> new NotFoundException("TERM_NOT_FOUND", "Dönem bulunamadı."));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private static void validate(TermRequest request) {
        if (!request.startsOn().isBefore(request.endsOn())) {
            throw new BadRequestException("INVALID_TERM_DATES", "Dönem başlangıcı bitişten önce olmalı.");
        }
        LocalDate opens = request.enrollmentOpensOn();
        LocalDate closes = request.enrollmentClosesOn();
        if (opens != null && closes != null && opens.isAfter(closes)) {
            throw new BadRequestException("INVALID_TERM_DATES", "Kayıt penceresi açılışı kapanıştan sonra olamaz.");
        }
        if (closes != null && closes.isAfter(request.endsOn())) {
            throw new BadRequestException("INVALID_TERM_DATES", "Kayıt penceresi dönem bitişinden sonra kapanamaz.");
        }
    }
}
