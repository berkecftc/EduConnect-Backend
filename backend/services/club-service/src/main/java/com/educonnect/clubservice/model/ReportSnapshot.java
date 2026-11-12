package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class ReportSnapshot {

    @Column(name = "snapshot_active_members")
    private Long activeMembers;

    @Column(name = "snapshot_ended_memberships")
    private Long endedMemberships;

    @Column(name = "snapshot_announcements")
    private Long announcements;

    @Column(name = "snapshot_meetings")
    private Long meetings;

    @Column(name = "snapshot_decisions")
    private Long decisions;

    @Column(name = "snapshot_planned_income", precision = 12, scale = 2)
    private BigDecimal plannedIncome;

    @Column(name = "snapshot_planned_expense", precision = 12, scale = 2)
    private BigDecimal plannedExpense;

    @Column(name = "snapshot_income", precision = 12, scale = 2)
    private BigDecimal income;

    @Column(name = "snapshot_expense", precision = 12, scale = 2)
    private BigDecimal expense;

    @Column(name = "snapshot_events")
    private Long events;

    @Column(name = "snapshot_completed_events")
    private Long completedEvents;

    @Column(name = "snapshot_cancelled_events")
    private Long cancelledEvents;

    @Column(name = "snapshot_registrations")
    private Long registrations;

    @Column(name = "snapshot_attendances")
    private Long attendances;

    protected ReportSnapshot() {
    }

    public ReportSnapshot(long activeMembers, long endedMemberships, long announcements, long meetings, long decisions,
                          BigDecimal plannedIncome, BigDecimal plannedExpense, BigDecimal income, BigDecimal expense,
                          long events, long completedEvents, long cancelledEvents, long registrations, long attendances) {
        this.activeMembers = activeMembers;
        this.endedMemberships = endedMemberships;
        this.announcements = announcements;
        this.meetings = meetings;
        this.decisions = decisions;
        this.plannedIncome = plannedIncome;
        this.plannedExpense = plannedExpense;
        this.income = income;
        this.expense = expense;
        this.events = events;
        this.completedEvents = completedEvents;
        this.cancelledEvents = cancelledEvents;
        this.registrations = registrations;
        this.attendances = attendances;
    }

    public Long getActiveMembers() { return activeMembers; }
    public Long getEndedMemberships() { return endedMemberships; }
    public Long getAnnouncements() { return announcements; }
    public Long getMeetings() { return meetings; }
    public Long getDecisions() { return decisions; }
    public BigDecimal getPlannedIncome() { return plannedIncome; }
    public BigDecimal getPlannedExpense() { return plannedExpense; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getExpense() { return expense; }
    public Long getEvents() { return events; }
    public Long getCompletedEvents() { return completedEvents; }
    public Long getCancelledEvents() { return cancelledEvents; }
    public Long getRegistrations() { return registrations; }
    public Long getAttendances() { return attendances; }
}
