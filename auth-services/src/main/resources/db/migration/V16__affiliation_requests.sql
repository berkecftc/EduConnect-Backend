ALTER TABLE auth_db.student_requests ADD COLUMN IF NOT EXISTS user_id uuid;

CREATE UNIQUE INDEX uq_student_requests_user ON auth_db.student_requests (user_id) WHERE user_id IS NOT NULL;
