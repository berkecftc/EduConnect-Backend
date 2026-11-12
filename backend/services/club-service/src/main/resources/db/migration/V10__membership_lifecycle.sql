ALTER TABLE club_db.club_memberships
    ADD COLUMN valid_until date,
    ADD COLUMN ended_at timestamp(6) without time zone,
    ADD COLUMN end_reason varchar(20),
    ADD CONSTRAINT ck_club_memberships_end_reason CHECK (end_reason IN ('LEFT', 'EXPELLED', 'EXPIRED'));

UPDATE club_db.club_memberships
SET valid_until = CASE
        WHEN current_date > make_date(extract(year FROM current_date)::int, 9, 30) - 30
            THEN make_date(extract(year FROM current_date)::int + 1, 9, 30)
        ELSE make_date(extract(year FROM current_date)::int, 9, 30)
    END
WHERE is_active;

CREATE INDEX idx_club_memberships_expiry ON club_db.club_memberships (valid_until)
    WHERE is_active AND club_role = 'MEMBER';

ALTER TABLE club_db.club_approval_requests
    ADD COLUMN response_note text,
    DROP CONSTRAINT ck_club_approval_requests_type,
    ADD CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE', 'MEMBER_EXPULSION'));

DROP INDEX club_db.ux_club_approval_requests_pending_subject;
CREATE UNIQUE INDEX ux_club_approval_requests_pending_subject
    ON club_db.club_approval_requests (club_id, type, subject_user_id)
    WHERE status IN ('PENDING_PRESIDENT', 'PENDING_ADVISOR') AND type IN ('ROLE_CHANGE', 'RESIGNATION', 'MEMBER_EXPULSION');
