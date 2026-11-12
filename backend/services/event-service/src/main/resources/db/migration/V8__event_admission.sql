ALTER TABLE event_db.events
    ADD COLUMN audience varchar(20) NOT NULL DEFAULT 'MEMBERS_ONLY',
    ADD COLUMN admission varchar(20) NOT NULL DEFAULT 'APPROVAL_REQUIRED',
    ADD COLUMN capacity integer,
    ADD COLUMN registration_opens_at timestamp(6) without time zone,
    ADD COLUMN registration_closes_at timestamp(6) without time zone,
    ADD COLUMN cancel_until timestamp(6) without time zone,
    ADD CONSTRAINT ck_events_audience CHECK (audience IN ('MEMBERS_ONLY', 'ALL_STUDENTS', 'CAMPUS')),
    ADD CONSTRAINT ck_events_admission CHECK (admission IN ('AUTO_CONFIRM', 'APPROVAL_REQUIRED')),
    ADD CONSTRAINT ck_events_capacity CHECK (capacity IS NULL OR capacity > 0);

ALTER TABLE event_db.event_registrations
    ADD COLUMN status varchar(12) NOT NULL DEFAULT 'REGISTERED',
    ADD COLUMN cancelled_at timestamp(6) without time zone,
    ADD CONSTRAINT ck_event_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED', 'NO_SHOW'));

CREATE INDEX idx_event_registrations_event_status ON event_db.event_registrations (event_id, status);

ALTER TABLE event_db.event_participation_requests DROP CONSTRAINT event_participation_requests_status_check;
ALTER TABLE event_db.event_participation_requests ADD CONSTRAINT event_participation_requests_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CLOSED', 'WAITLISTED', 'WITHDRAWN'));
CREATE INDEX idx_event_participation_requests_event_status ON event_db.event_participation_requests (event_id, status, request_date);
