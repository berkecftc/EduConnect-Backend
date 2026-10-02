ALTER TABLE event_db.events
    ADD COLUMN cancellation_reason text,
    ADD COLUMN published_at timestamp(6) without time zone;
UPDATE event_db.events SET published_at = updated_at WHERE status IN ('ACTIVE', 'COMPLETED');

CREATE TABLE event_db.event_changes (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    details text,
    reason text,
    actor_id uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_event_changes_event FOREIGN KEY (event_id) REFERENCES event_db.events (id) ON DELETE CASCADE,
    CONSTRAINT ck_event_changes_kind CHECK (kind IN ('EDITED', 'RESUBMITTED', 'POSTPONED', 'RELOCATED', 'CANCELLED'))
);

CREATE INDEX idx_event_changes_event ON event_db.event_changes (event_id, created_at);
