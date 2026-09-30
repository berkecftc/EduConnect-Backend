CREATE TABLE club_db.club_meetings (
    id uuid PRIMARY KEY,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    request_id uuid NOT NULL UNIQUE REFERENCES club_db.club_approval_requests (id) ON DELETE CASCADE,
    meeting_at timestamp without time zone NOT NULL,
    academic_year integer NOT NULL,
    location varchar(200),
    agenda text NOT NULL,
    minutes text NOT NULL,
    board_size integer NOT NULL,
    quorum_met boolean NOT NULL,
    prepared_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    approved_at timestamp with time zone
);

CREATE INDEX idx_club_meetings_year ON club_db.club_meetings (club_id, academic_year, meeting_at DESC);

CREATE TABLE club_db.club_meeting_attendees (
    meeting_id uuid NOT NULL REFERENCES club_db.club_meetings (id) ON DELETE CASCADE,
    student_id uuid NOT NULL,
    PRIMARY KEY (meeting_id, student_id)
);

CREATE TABLE club_db.club_meeting_decisions (
    id uuid PRIMARY KEY,
    meeting_id uuid NOT NULL REFERENCES club_db.club_meetings (id) ON DELETE CASCADE,
    club_id uuid NOT NULL,
    academic_year integer NOT NULL,
    item_order integer NOT NULL,
    text text NOT NULL,
    decision_number integer,
    CONSTRAINT uq_club_meeting_decisions_number UNIQUE (club_id, academic_year, decision_number)
);

CREATE INDEX idx_club_meeting_decisions_meeting ON club_db.club_meeting_decisions (meeting_id, item_order);

ALTER TABLE club_db.club_approval_requests
    DROP CONSTRAINT ck_club_approval_requests_type,
    ADD CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE', 'MEMBER_EXPULSION',
                        'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE', 'CLUB_ANNOUNCEMENT', 'CLUB_BUDGET',
                        'CLUB_FINANCE_ENTRY', 'CLUB_SPONSORSHIP', 'CLUB_MEETING_MINUTES'));
