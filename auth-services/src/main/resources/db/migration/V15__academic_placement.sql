ALTER TABLE auth_db.student_requests
    ADD COLUMN program_id uuid,
    ADD COLUMN entry_year integer;

ALTER TABLE auth_db.academician_requests
    ADD COLUMN department_id uuid;
