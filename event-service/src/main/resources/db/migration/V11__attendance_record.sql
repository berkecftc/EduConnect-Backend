ALTER TABLE event_db.event_registrations
    ADD COLUMN checked_in_at timestamp(6) without time zone,
    ADD COLUMN checked_in_by uuid,
    ADD COLUMN check_in_method varchar(10),
    ADD CONSTRAINT ck_event_registrations_check_in_method CHECK (check_in_method IS NULL OR check_in_method IN ('QR', 'MANUAL'));
