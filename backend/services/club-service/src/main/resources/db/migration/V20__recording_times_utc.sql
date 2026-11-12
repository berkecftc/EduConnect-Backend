ALTER TABLE club_db.archived_clubs
    ALTER COLUMN deleted_at TYPE timestamptz USING deleted_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE club_db.club_creation_requests
    ALTER COLUMN request_date TYPE timestamptz USING request_date AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN processed_at TYPE timestamptz USING processed_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE club_db.club_founders
    ALTER COLUMN responded_at TYPE timestamptz USING responded_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE club_db.club_memberships
    ALTER COLUMN term_start_date TYPE timestamptz USING term_start_date AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN term_end_date TYPE timestamptz USING term_end_date AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN ended_at TYPE timestamptz USING ended_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE club_db.club_membership_requests
    ALTER COLUMN request_date TYPE timestamptz USING request_date AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN processed_date TYPE timestamptz USING processed_date AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN recommended_at TYPE timestamptz USING recommended_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE club_db.club_position_terms
    ALTER COLUMN started_at TYPE timestamptz USING started_at AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN ended_at TYPE timestamptz USING ended_at AT TIME ZONE 'Europe/Istanbul';
