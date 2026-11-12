ALTER TABLE course_db.course_announcements
    ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE course_db.course_applications
    ALTER COLUMN application_date TYPE timestamptz USING application_date AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN processed_date TYPE timestamptz USING processed_date AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE course_db.student_course_enrollments
    ALTER COLUMN enrollment_date TYPE timestamptz USING enrollment_date AT TIME ZONE 'Europe/Istanbul';
