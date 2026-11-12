ALTER TABLE event_db.events
    ALTER COLUMN club_id DROP NOT NULL,
    ALTER COLUMN club_name DROP NOT NULL,
    ADD COLUMN organizer_name varchar(200),
    ADD CONSTRAINT ck_events_organizer CHECK ((club_id IS NOT NULL AND audience <> 'CAMPUS') OR (club_id IS NULL AND audience = 'CAMPUS'));
