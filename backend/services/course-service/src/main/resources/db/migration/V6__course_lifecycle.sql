ALTER TABLE course_db.courses
    ADD COLUMN status varchar(12) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN completed_at timestamp with time zone,
    ADD COLUMN archived_at timestamp with time zone,
    ADD CONSTRAINT ck_courses_status CHECK (status IN ('DRAFT', 'OPEN', 'ACTIVE', 'COMPLETED', 'ARCHIVED'));

UPDATE course_db.courses c
SET status = CASE
        WHEN t.ends_on < current_date THEN 'COMPLETED'
        WHEN t.starts_on > current_date THEN 'OPEN'
        ELSE 'ACTIVE' END,
    completed_at = CASE WHEN t.ends_on < current_date THEN now() END
FROM course_db.terms t
WHERE t.id = c.term_id;

CREATE INDEX idx_courses_status ON course_db.courses (status);
