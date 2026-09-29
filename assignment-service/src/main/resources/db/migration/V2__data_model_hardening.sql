ALTER TABLE assignment_db.assignments
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE assignment_db.assignment_submissions
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

DELETE FROM assignment_db.assignment_submissions s
    WHERE NOT EXISTS (SELECT 1 FROM assignment_db.assignments a WHERE a.id = s.assignment_id);

ALTER TABLE assignment_db.assignment_submissions
    ADD CONSTRAINT fk_assignment_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignment_db.assignments (id) ON DELETE CASCADE;

CREATE INDEX idx_assignments_course ON assignment_db.assignments (course_id);
CREATE INDEX idx_assignment_submissions_student ON assignment_db.assignment_submissions (student_id);
