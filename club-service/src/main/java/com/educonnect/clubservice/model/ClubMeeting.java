package com.educonnect.clubservice.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "club_meetings")
public class ClubMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "meeting_at", nullable = false)
    private LocalDateTime meetingAt;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String agenda;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String minutes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "club_meeting_attendees", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "student_id", nullable = false)
    private Set<UUID> attendeeIds = new LinkedHashSet<>();

    @Column(name = "board_size", nullable = false)
    private int boardSize;

    @Column(name = "quorum_met", nullable = false)
    private boolean quorumMet;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected ClubMeeting() {
    }

    public ClubMeeting(UUID clubId, UUID requestId, LocalDateTime meetingAt, int academicYear, String location,
                       String agenda, String minutes, Set<UUID> attendeeIds, int boardSize, boolean quorumMet,
                       UUID preparedBy, Instant createdAt) {
        this.clubId = clubId;
        this.requestId = requestId;
        this.meetingAt = meetingAt;
        this.academicYear = academicYear;
        this.location = location;
        this.agenda = agenda;
        this.minutes = minutes;
        this.attendeeIds = new LinkedHashSet<>(attendeeIds);
        this.boardSize = boardSize;
        this.quorumMet = quorumMet;
        this.preparedBy = preparedBy;
        this.createdAt = createdAt;
    }

    public void approve(Instant at) {
        this.approvedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getRequestId() { return requestId; }
    public LocalDateTime getMeetingAt() { return meetingAt; }
    public int getAcademicYear() { return academicYear; }
    public String getLocation() { return location; }
    public String getAgenda() { return agenda; }
    public String getMinutes() { return minutes; }
    public Set<UUID> getAttendeeIds() { return Set.copyOf(attendeeIds); }
    public int getBoardSize() { return boardSize; }
    public boolean isQuorumMet() { return quorumMet; }
    public UUID getPreparedBy() { return preparedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
