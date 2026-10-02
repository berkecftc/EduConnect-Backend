ALTER TABLE event_db.events RENAME COLUMN event_time TO starts_at;
ALTER TABLE event_db.events
    ADD COLUMN ends_at timestamp(6) without time zone,
    ADD COLUMN speakers text;
UPDATE event_db.events SET ends_at = starts_at + interval '2 hours';
ALTER TABLE event_db.events
    ALTER COLUMN ends_at SET NOT NULL,
    ADD CONSTRAINT ck_events_schedule CHECK (ends_at > starts_at);

ALTER INDEX event_db.idx_events_status_time RENAME TO idx_events_status_starts;
CREATE INDEX idx_events_status_ends ON event_db.events (status, ends_at);

ALTER TABLE event_db.event_participation_requests DROP CONSTRAINT event_participation_requests_status_check;
ALTER TABLE event_db.event_participation_requests ADD CONSTRAINT event_participation_requests_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CLOSED'));
