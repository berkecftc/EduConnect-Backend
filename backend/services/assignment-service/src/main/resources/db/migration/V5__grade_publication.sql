ALTER TABLE assignment_db.assignments
    ADD COLUMN grades_published_at timestamp with time zone,
    ADD COLUMN grades_published_by uuid;

UPDATE assignment_db.assignments a
SET grades_published_at = now()
WHERE EXISTS (SELECT 1 FROM assignment_db.assignment_submissions s WHERE s.assignment_id = a.id AND s.grade IS NOT NULL);

CREATE TABLE assignment_db.grade_changes (
    id uuid PRIMARY KEY,
    submission_id uuid NOT NULL,
    old_grade numeric(6, 2),
    new_grade numeric(6, 2),
    feedback_changed boolean NOT NULL DEFAULT false,
    after_publication boolean NOT NULL DEFAULT false,
    reason varchar(500),
    changed_by uuid,
    changed_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_grade_changes_submission FOREIGN KEY (submission_id)
        REFERENCES assignment_db.assignment_submissions (id) ON DELETE CASCADE
);

CREATE INDEX idx_grade_changes_submission ON assignment_db.grade_changes (submission_id, changed_at);
