ALTER TABLE user_db.archived_students
    ALTER COLUMN deleted_at TYPE timestamptz USING deleted_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE user_db.archived_academicians
    ALTER COLUMN deleted_at TYPE timestamptz USING deleted_at AT TIME ZONE 'Europe/Istanbul';
