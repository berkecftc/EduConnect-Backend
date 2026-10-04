ALTER TABLE event_db.events
    ALTER COLUMN published_at TYPE timestamptz USING published_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE event_db.event_participation_requests
    ALTER COLUMN request_date TYPE timestamptz USING request_date AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN processed_date TYPE timestamptz USING processed_date AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE event_db.event_registrations
    ALTER COLUMN registration_time TYPE timestamptz USING registration_time AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN cancelled_at TYPE timestamptz USING cancelled_at AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN checked_in_at TYPE timestamptz USING checked_in_at AT TIME ZONE 'Europe/Istanbul';
