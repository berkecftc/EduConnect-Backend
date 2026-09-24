ALTER TABLE event_db.events
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE event_db.event_participation_requests ADD COLUMN version bigint NOT NULL DEFAULT 0;

DELETE FROM event_db.event_registrations r
    WHERE NOT EXISTS (SELECT 1 FROM event_db.events e WHERE e.id = r.event_id);
DELETE FROM event_db.event_participation_requests r
    WHERE NOT EXISTS (SELECT 1 FROM event_db.events e WHERE e.id = r.event_id);

ALTER TABLE event_db.event_registrations
    ADD CONSTRAINT fk_event_registrations_event FOREIGN KEY (event_id) REFERENCES event_db.events (id) ON DELETE CASCADE;
ALTER TABLE event_db.event_participation_requests
    ADD CONSTRAINT fk_event_participation_requests_event FOREIGN KEY (event_id) REFERENCES event_db.events (id) ON DELETE CASCADE;

CREATE INDEX idx_events_club_status ON event_db.events (club_id, status);
CREATE INDEX idx_events_status_time ON event_db.events (status, event_time);
CREATE INDEX idx_events_created_by ON event_db.events (created_by_student_id);
CREATE INDEX idx_event_registrations_student ON event_db.event_registrations (student_id);
CREATE INDEX idx_event_participation_requests_student_status ON event_db.event_participation_requests (student_id, status);
