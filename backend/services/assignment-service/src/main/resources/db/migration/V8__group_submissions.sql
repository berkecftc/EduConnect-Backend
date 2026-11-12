ALTER TABLE assignment_db.assignments
    ADD COLUMN group_set_id uuid,
    ADD CONSTRAINT fk_assignments_group_set FOREIGN KEY (group_set_id) REFERENCES assignment_db.group_sets (id);

CREATE INDEX idx_assignments_group_set ON assignment_db.assignments (group_set_id);

ALTER TABLE assignment_db.assignment_submissions
    DROP CONSTRAINT ukjpaoiqlq2bm3rv52lcri47g4s,
    ADD COLUMN group_id uuid,
    ADD CONSTRAINT fk_assignment_submissions_group FOREIGN KEY (group_id) REFERENCES assignment_db.course_groups (id);

CREATE UNIQUE INDEX uq_assignment_submissions_student
    ON assignment_db.assignment_submissions (assignment_id, student_id) WHERE group_id IS NULL;
CREATE UNIQUE INDEX uq_assignment_submissions_group
    ON assignment_db.assignment_submissions (assignment_id, group_id) WHERE group_id IS NOT NULL;
CREATE INDEX idx_assignment_submissions_group ON assignment_db.assignment_submissions (group_id);

ALTER TABLE assignment_db.submission_versions
    ADD COLUMN submitted_by uuid;

UPDATE assignment_db.submission_versions v
SET submitted_by = s.student_id
FROM assignment_db.assignment_submissions s
WHERE s.id = v.submission_id;

CREATE TABLE assignment_db.member_grades (
    id uuid PRIMARY KEY,
    submission_id uuid NOT NULL,
    student_id uuid NOT NULL,
    grade numeric(6, 2) NOT NULL,
    reason varchar(500) NOT NULL,
    changed_by uuid,
    changed_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_member_grades_submission FOREIGN KEY (submission_id)
        REFERENCES assignment_db.assignment_submissions (id) ON DELETE CASCADE,
    CONSTRAINT uq_member_grades_student UNIQUE (submission_id, student_id),
    CONSTRAINT ck_member_grades_grade CHECK (grade >= 0)
);

CREATE INDEX idx_member_grades_student ON assignment_db.member_grades (student_id);

ALTER TABLE assignment_db.grade_changes
    ADD COLUMN student_id uuid;
