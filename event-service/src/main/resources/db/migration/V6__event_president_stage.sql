ALTER TABLE event_db.events DROP CONSTRAINT events_status_check;
ALTER TABLE event_db.events ADD CONSTRAINT events_status_check
    CHECK (status IN ('PENDING_PRESIDENT', 'PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED', 'COMPLETED'));
ALTER TABLE event_db.events ADD COLUMN rejection_reason text;
