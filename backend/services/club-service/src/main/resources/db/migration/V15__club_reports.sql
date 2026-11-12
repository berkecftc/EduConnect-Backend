CREATE TABLE club_db.club_reports (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    type varchar(10) NOT NULL CHECK (type IN ('ACTIVITY', 'AUDIT')),
    academic_year integer NOT NULL,
    status varchar(10) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED')),
    body text,
    finance_note text,
    recommendations text,
    snapshot_active_members bigint,
    snapshot_ended_memberships bigint,
    snapshot_announcements bigint,
    snapshot_meetings bigint,
    snapshot_decisions bigint,
    snapshot_planned_income numeric(12, 2),
    snapshot_planned_expense numeric(12, 2),
    snapshot_income numeric(12, 2),
    snapshot_expense numeric(12, 2),
    snapshot_events bigint,
    snapshot_completed_events bigint,
    snapshot_cancelled_events bigint,
    snapshot_registrations bigint,
    snapshot_attendances bigint,
    request_id uuid REFERENCES club_db.club_approval_requests (id) ON DELETE SET NULL,
    created_by uuid NOT NULL,
    updated_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    submitted_at timestamp with time zone,
    approved_at timestamp with time zone,
    CONSTRAINT uq_club_reports_year UNIQUE (club_id, type, academic_year)
);

CREATE INDEX idx_club_reports_request ON club_db.club_reports (request_id);

ALTER TABLE club_db.club_approval_requests
    DROP CONSTRAINT ck_club_approval_requests_type,
    ADD CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE', 'MEMBER_EXPULSION',
                        'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE', 'CLUB_ANNOUNCEMENT', 'CLUB_BUDGET',
                        'CLUB_FINANCE_ENTRY', 'CLUB_SPONSORSHIP', 'CLUB_MEETING_MINUTES', 'CLUB_ACTIVITY_REPORT',
                        'CLUB_AUDIT_REPORT'));
