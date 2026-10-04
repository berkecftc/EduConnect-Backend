ALTER TABLE assignment_db.assignments
    ADD COLUMN ai_policy varchar(30) NOT NULL DEFAULT 'GUIDANCE',
    ADD CONSTRAINT ck_assignments_ai_policy CHECK (ai_policy IN ('NONE', 'GUIDANCE', 'ALLOWED_WITH_DISCLOSURE'));

ALTER TABLE assignment_db.assignment_submissions
    ADD COLUMN ai_used boolean,
    ADD COLUMN ai_note varchar(1000);
