CREATE TABLE club_db.club_position_terms (
    id uuid PRIMARY KEY,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    student_id uuid NOT NULL,
    position varchar(40) NOT NULL,
    started_at timestamp without time zone NOT NULL,
    ended_at timestamp without time zone,
    end_reason varchar(20),
    CONSTRAINT ck_club_position_terms_reason
        CHECK (end_reason IN ('CHANGED', 'RESIGNED', 'REMOVED', 'LEFT_CLUB', 'EXPELLED', 'HANDOVER', 'CLUB_CLOSED'))
);

CREATE INDEX idx_club_position_terms_club ON club_db.club_position_terms (club_id, started_at DESC);
CREATE INDEX idx_club_position_terms_student ON club_db.club_position_terms (student_id, started_at DESC);
CREATE UNIQUE INDEX ux_club_position_terms_open ON club_db.club_position_terms (club_id, student_id) WHERE ended_at IS NULL;

INSERT INTO club_db.club_position_terms (id, club_id, student_id, position, started_at)
SELECT gen_random_uuid(), m.club_id, m.student_id, m.club_role,
       coalesce(m.term_start_date, m.created_at AT TIME ZONE 'UTC')
FROM club_db.club_memberships m
         JOIN club_db.clubs c ON c.id = m.club_id
WHERE m.is_active
  AND m.club_role <> 'MEMBER'
  AND c.status <> 'CLOSED';

ALTER TABLE club_db.club_announcements ALTER COLUMN prepared_by DROP NOT NULL;
ALTER TABLE club_db.club_budgets ALTER COLUMN prepared_by DROP NOT NULL;
ALTER TABLE club_db.club_finance_entries ALTER COLUMN prepared_by DROP NOT NULL;
ALTER TABLE club_db.club_sponsorships ALTER COLUMN prepared_by DROP NOT NULL;
ALTER TABLE club_db.club_meetings ALTER COLUMN prepared_by DROP NOT NULL;
ALTER TABLE club_db.club_reports ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE club_db.club_reports ALTER COLUMN updated_by DROP NOT NULL;
