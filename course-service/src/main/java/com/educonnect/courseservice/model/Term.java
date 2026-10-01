package com.educonnect.courseservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "terms")
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TermSeason season;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "enrollment_opens_on")
    private LocalDate enrollmentOpensOn;

    @Column(name = "enrollment_closes_on")
    private LocalDate enrollmentClosesOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Term() {
    }

    public Term(int academicYear, TermSeason season) {
        this.academicYear = academicYear;
        this.season = season;
    }

    public void schedule(LocalDate startsOn, LocalDate endsOn, LocalDate enrollmentOpensOn, LocalDate enrollmentClosesOn) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.enrollmentOpensOn = enrollmentOpensOn;
        this.enrollmentClosesOn = enrollmentClosesOn;
    }

    public void rename(int academicYear, TermSeason season) {
        this.academicYear = academicYear;
        this.season = season;
    }

    public String label() {
        if (academicYear < 2000 || academicYear > 2200) {
            throw new IllegalStateException("Unsupported academic year: " + academicYear);
        }
        return (academicYear - 1) + "-" + academicYear + " " + season.displayName();
    }

    public boolean acceptsApplications(LocalDate today) {
        return (enrollmentOpensOn == null || !today.isBefore(enrollmentOpensOn))
                && (enrollmentClosesOn == null || !today.isAfter(enrollmentClosesOn));
    }

    public boolean isAfterEnrollment(LocalDate day) {
        return enrollmentClosesOn != null && day.isAfter(enrollmentClosesOn);
    }

    public boolean hasEnded(LocalDate today) {
        return today.isAfter(endsOn);
    }

    public UUID getId() { return id; }
    public int getAcademicYear() { return academicYear; }
    public TermSeason getSeason() { return season; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public LocalDate getEnrollmentOpensOn() { return enrollmentOpensOn; }
    public LocalDate getEnrollmentClosesOn() { return enrollmentClosesOn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
