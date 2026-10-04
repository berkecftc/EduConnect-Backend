CREATE TABLE event_db.event_staff (
    id          uuid PRIMARY KEY,
    event_id    uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    status      varchar(20) NOT NULL,
    proposed_by uuid        NOT NULL,
    approved_by uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    decided_at  timestamptz,
    CONSTRAINT fk_event_staff_event FOREIGN KEY (event_id) REFERENCES event_db.events (id) ON DELETE CASCADE,
    CONSTRAINT uq_event_staff UNIQUE (event_id, user_id),
    CONSTRAINT ck_event_staff_status CHECK (status IN ('PENDING', 'APPROVED'))
);

CREATE INDEX idx_event_staff_user ON event_db.event_staff (user_id, status);
