CREATE TABLE club_db.club_announcements (
    id uuid PRIMARY KEY,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    request_id uuid NOT NULL UNIQUE REFERENCES club_db.club_approval_requests (id) ON DELETE CASCADE,
    title varchar(200) NOT NULL,
    body text NOT NULL,
    prepared_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    removed_at timestamp with time zone,
    removed_by uuid
);

CREATE INDEX idx_club_announcements_published ON club_db.club_announcements (club_id, published_at DESC)
    WHERE published_at IS NOT NULL AND removed_at IS NULL;

ALTER TABLE club_db.club_approval_requests
    DROP CONSTRAINT ck_club_approval_requests_type,
    ADD CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE', 'MEMBER_EXPULSION',
                        'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE', 'CLUB_ANNOUNCEMENT'));
