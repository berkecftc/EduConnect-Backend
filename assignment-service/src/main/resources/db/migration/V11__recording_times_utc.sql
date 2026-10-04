ALTER TABLE assignment_db.assignment_submissions
    ALTER COLUMN submitted_at TYPE timestamptz USING submitted_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE assignment_db.submission_versions
    ALTER COLUMN submitted_at TYPE timestamptz USING submitted_at AT TIME ZONE 'Europe/Istanbul';
