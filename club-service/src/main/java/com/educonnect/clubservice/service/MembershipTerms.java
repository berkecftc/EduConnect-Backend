package com.educonnect.clubservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;

@Component
public class MembershipTerms {

    private final MonthDay yearEnd;
    private final int renewalWindowDays;
    private final Clock clock;

    @Autowired
    public MembershipTerms(@Value("${educonnect.club.membership.year-end:09-30}") String yearEnd,
                           @Value("${educonnect.club.membership.renewal-window-days:30}") int renewalWindowDays) {
        this(MonthDay.parse("--" + yearEnd), renewalWindowDays, Clock.systemDefaultZone());
    }

    MembershipTerms(MonthDay yearEnd, int renewalWindowDays, Clock clock) {
        this.yearEnd = yearEnd;
        this.renewalWindowDays = renewalWindowDays;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate validUntilFor(LocalDate day) {
        LocalDate end = yearEnd.atYear(day.getYear());
        if (day.isAfter(end.minusDays(renewalWindowDays))) {
            end = yearEnd.atYear(day.getYear() + 1);
        }
        return end;
    }

    public LocalDate currentValidUntil() {
        return validUntilFor(today());
    }

    public boolean inRenewalWindow(LocalDate validUntil) {
        return validUntil != null && today().isAfter(validUntil.minusDays(renewalWindowDays));
    }
}
