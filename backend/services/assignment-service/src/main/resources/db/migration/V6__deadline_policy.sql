ALTER TABLE assignment_db.assignments
    ADD COLUMN late_until timestamp(6) without time zone,
    ADD COLUMN late_penalty_percent numeric(5, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_assignments_late_penalty CHECK (late_penalty_percent >= 0 AND late_penalty_percent <= 100),
    ADD CONSTRAINT ck_assignments_late_until CHECK (late_until IS NULL OR due_date IS NULL OR late_until > due_date);

ALTER TABLE assignment_db.assignment_submissions
    ADD COLUMN text_content text;

CREATE TABLE assignment_db.submission_versions (
    id uuid PRIMARY KEY,
    submission_id uuid NOT NULL,
    version_no integer NOT NULL,
    file_url character varying(255),
    text_content text,
    submitted_at timestamp(6) without time zone NOT NULL,
    late boolean NOT NULL,
    CONSTRAINT fk_submission_versions_submission FOREIGN KEY (submission_id)
        REFERENCES assignment_db.assignment_submissions (id) ON DELETE CASCADE,
    CONSTRAINT uq_submission_versions_number UNIQUE (submission_id, version_no)
);

CREATE INDEX idx_submission_versions_file ON assignment_db.submission_versions (file_url);

INSERT INTO assignment_db.submission_versions (id, submission_id, version_no, file_url, submitted_at, late)
SELECT gen_random_uuid(), s.id, 1, s.submission_file_url, s.submitted_at, s.is_late
FROM assignment_db.assignment_submissions s;

CREATE TABLE assignment_db.assignment_extensions (
    id uuid PRIMARY KEY,
    assignment_id uuid NOT NULL,
    student_id uuid NOT NULL,
    due_date timestamp(6) without time zone NOT NULL,
    reason varchar(500),
    granted_by uuid,
    granted_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_assignment_extensions_assignment FOREIGN KEY (assignment_id)
        REFERENCES assignment_db.assignments (id) ON DELETE CASCADE,
    CONSTRAINT uq_assignment_extensions_student UNIQUE (assignment_id, student_id)
);

CREATE INDEX idx_assignment_extensions_student ON assignment_db.assignment_extensions (student_id);
