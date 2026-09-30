package com.educonnect.clubservice.model;

public enum ElectionBallot {
    PRESIDENT(ClubPosition.PRESIDENT),
    BOARD(ClubPosition.BOARD_MEMBER),
    AUDIT(ClubPosition.AUDITOR);

    private final ClubPosition position;

    ElectionBallot(ClubPosition position) {
        this.position = position;
    }

    public ClubPosition position() {
        return position;
    }
}
