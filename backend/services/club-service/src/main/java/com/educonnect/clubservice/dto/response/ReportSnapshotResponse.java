package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ReportSnapshot;

import java.math.BigDecimal;

public record ReportSnapshotResponse(Long activeMembers,
                                     Long endedMemberships,
                                     Long announcements,
                                     Long meetings,
                                     Long decisions,
                                     BigDecimal plannedIncome,
                                     BigDecimal plannedExpense,
                                     BigDecimal income,
                                     BigDecimal expense,
                                     BigDecimal balance,
                                     Long events,
                                     Long completedEvents,
                                     Long cancelledEvents,
                                     Long registrations,
                                     Long attendances) {

    public static ReportSnapshotResponse of(ReportSnapshot snapshot) {
        if (snapshot == null || snapshot.getActiveMembers() == null) {
            return null;
        }
        BigDecimal balance = snapshot.getIncome() != null && snapshot.getExpense() != null
                ? snapshot.getIncome().subtract(snapshot.getExpense())
                : null;
        return new ReportSnapshotResponse(snapshot.getActiveMembers(), snapshot.getEndedMemberships(),
                snapshot.getAnnouncements(), snapshot.getMeetings(), snapshot.getDecisions(), snapshot.getPlannedIncome(),
                snapshot.getPlannedExpense(), snapshot.getIncome(), snapshot.getExpense(), balance, snapshot.getEvents(),
                snapshot.getCompletedEvents(), snapshot.getCancelledEvents(), snapshot.getRegistrations(),
                snapshot.getAttendances());
    }
}
