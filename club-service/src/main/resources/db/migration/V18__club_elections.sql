CREATE TABLE club_db.club_elections (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    status varchar(20) NOT NULL
        CHECK (status IN ('CANDIDACY', 'VOTING', 'AWAITING_APPROVAL', 'COMPLETED', 'CANCELLED')),
    board_seats integer NOT NULL CHECK (board_seats BETWEEN 0 AND 6),
    audit_seats integer NOT NULL CHECK (audit_seats BETWEEN 0 AND 3),
    note text,
    opened_by uuid,
    opened_at timestamp with time zone NOT NULL,
    voting_started_at timestamp with time zone,
    closed_at timestamp with time zone,
    eligible_voters integer,
    voters integer,
    request_id uuid REFERENCES club_db.club_approval_requests (id) ON DELETE SET NULL,
    completed_at timestamp with time zone
);

CREATE UNIQUE INDEX ux_club_elections_open ON club_db.club_elections (club_id)
    WHERE status IN ('CANDIDACY', 'VOTING', 'AWAITING_APPROVAL');
CREATE INDEX idx_club_elections_request ON club_db.club_elections (request_id);

CREATE TABLE club_db.club_election_candidates (
    id uuid PRIMARY KEY,
    election_id uuid NOT NULL REFERENCES club_db.club_elections (id) ON DELETE CASCADE,
    ballot varchar(10) NOT NULL CHECK (ballot IN ('PRESIDENT', 'BOARD', 'AUDIT')),
    student_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    votes integer,
    elected boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_club_election_candidates_student UNIQUE (election_id, student_id)
);

CREATE TABLE club_db.club_election_ballots_cast (
    id uuid PRIMARY KEY,
    election_id uuid NOT NULL REFERENCES club_db.club_elections (id) ON DELETE CASCADE,
    ballot varchar(10) NOT NULL CHECK (ballot IN ('PRESIDENT', 'BOARD', 'AUDIT')),
    voter_id uuid NOT NULL,
    cast_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_club_election_ballots_cast_voter UNIQUE (election_id, ballot, voter_id)
);

CREATE TABLE club_db.club_election_votes (
    id uuid PRIMARY KEY,
    election_id uuid NOT NULL REFERENCES club_db.club_elections (id) ON DELETE CASCADE,
    candidate_id uuid NOT NULL REFERENCES club_db.club_election_candidates (id) ON DELETE CASCADE
);

CREATE INDEX idx_club_election_votes_election ON club_db.club_election_votes (election_id);

ALTER TABLE club_db.club_approval_requests
    DROP CONSTRAINT ck_club_approval_requests_type,
    ADD CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE', 'MEMBER_EXPULSION',
                        'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE', 'CLUB_ANNOUNCEMENT', 'CLUB_BUDGET',
                        'CLUB_FINANCE_ENTRY', 'CLUB_SPONSORSHIP', 'CLUB_MEETING_MINUTES', 'CLUB_ACTIVITY_REPORT',
                        'CLUB_AUDIT_REPORT', 'CLUB_ELECTION'));
