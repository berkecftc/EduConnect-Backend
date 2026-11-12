ALTER TABLE assignment_db.assignments
    ADD COLUMN type varchar(12) NOT NULL DEFAULT 'HOMEWORK',
    ADD COLUMN weight numeric(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN max_points numeric(6, 2) NOT NULL DEFAULT 100,
    ADD CONSTRAINT ck_assignments_type CHECK (type IN ('HOMEWORK', 'PROJECT', 'QUIZ', 'LAB', 'MIDTERM', 'FINAL', 'OTHER')),
    ADD CONSTRAINT ck_assignments_weight CHECK (weight >= 0 AND weight <= 100),
    ADD CONSTRAINT ck_assignments_max_points CHECK (max_points > 0 AND max_points <= 1000);

ALTER TABLE assignment_db.assignment_submissions
    ALTER COLUMN grade TYPE numeric(6, 2),
    ADD CONSTRAINT ck_assignment_submissions_grade CHECK (grade IS NULL OR grade >= 0);

CREATE TABLE assignment_db.assignment_changes (
    id uuid PRIMARY KEY,
    assignment_id uuid NOT NULL,
    field varchar(20) NOT NULL,
    old_value text,
    new_value text,
    changed_by uuid,
    changed_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_assignment_changes_assignment FOREIGN KEY (assignment_id)
        REFERENCES assignment_db.assignments (id) ON DELETE CASCADE
);

CREATE INDEX idx_assignment_changes_assignment ON assignment_db.assignment_changes (assignment_id, changed_at);
