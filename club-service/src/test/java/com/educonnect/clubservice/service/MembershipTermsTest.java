package com.educonnect.clubservice.service;

import com.educonnect.common.web.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipTermsTest {

    private MembershipTerms termsOn(LocalDate today, String yearEnd) {
        Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new MembershipTerms(MonthDay.parse("--" + yearEnd), 30, clock);
    }

    @Test
    void membershipRunsUntilTheEndOfTheCurrentAcademicYear() {
        MembershipTerms terms = termsOn(LocalDate.of(2026, 3, 15), "09-30");
        assertThat(terms.currentValidUntil()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void joiningInTheLastMonthCountsForTheNextAcademicYear() {
        assertThat(termsOn(LocalDate.of(2026, 9, 5), "09-30").currentValidUntil()).isEqualTo(LocalDate.of(2027, 9, 30));
        assertThat(termsOn(LocalDate.of(2026, 10, 10), "09-30").currentValidUntil()).isEqualTo(LocalDate.of(2027, 9, 30));
    }

    @Test
    void theYearEndIsConfigurable() {
        assertThat(termsOn(LocalDate.of(2026, 7, 1), "08-31").currentValidUntil()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void academicYearsOutsideTheSupportedRangeAreRejected() {
        MembershipTerms terms = termsOn(LocalDate.of(2026, 9, 10), "09-30");
        assertThat(terms.resolveAcademicYear(null)).isEqualTo(2026);
        assertThat(terms.resolveAcademicYear(2027)).isEqualTo(2027);
        assertThatThrownBy(() -> terms.resolveAcademicYear(Integer.MIN_VALUE)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> terms.academicYearStart(1999)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void renewalOpensThirtyDaysBeforeTheEnd() {
        MembershipTerms terms = termsOn(LocalDate.of(2026, 9, 10), "09-30");
        assertThat(terms.inRenewalWindow(LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(terms.inRenewalWindow(LocalDate.of(2027, 9, 30))).isFalse();
    }
}
