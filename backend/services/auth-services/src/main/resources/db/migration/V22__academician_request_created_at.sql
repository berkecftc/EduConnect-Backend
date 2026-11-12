UPDATE auth_db.academician_requests SET created_at = now() WHERE created_at IS NULL;

ALTER TABLE auth_db.academician_requests
    ALTER COLUMN created_at TYPE timestamp with time zone USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN created_at SET NOT NULL;
