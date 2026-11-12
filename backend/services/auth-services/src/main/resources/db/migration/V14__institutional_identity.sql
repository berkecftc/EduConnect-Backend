ALTER TABLE auth_db.users
    ADD COLUMN student_number varchar(32);

CREATE UNIQUE INDEX uq_users_student_number ON auth_db.users (student_number) WHERE student_number IS NOT NULL;
CREATE INDEX idx_student_requests_student_number ON auth_db.student_requests (student_number);
