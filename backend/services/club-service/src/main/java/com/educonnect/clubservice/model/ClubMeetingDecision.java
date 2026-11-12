package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "club_meeting_decisions")
public class ClubMeetingDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "decision_number")
    private Integer decisionNumber;

    protected ClubMeetingDecision() {
    }

    public ClubMeetingDecision(UUID meetingId, UUID clubId, int academicYear, int itemOrder, String text) {
        this.meetingId = meetingId;
        this.clubId = clubId;
        this.academicYear = academicYear;
        this.itemOrder = itemOrder;
        this.text = text;
    }

    public void number(int decisionNumber) {
        this.decisionNumber = decisionNumber;
    }

    public UUID getId() { return id; }
    public UUID getMeetingId() { return meetingId; }
    public UUID getClubId() { return clubId; }
    public int getAcademicYear() { return academicYear; }
    public int getItemOrder() { return itemOrder; }
    public String getText() { return text; }
    public Integer getDecisionNumber() { return decisionNumber; }
}
