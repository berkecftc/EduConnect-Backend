ALTER TABLE auth_db.users
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();

ALTER TABLE auth_db.student_requests
    ADD COLUMN IF NOT EXISTS created_at timestamp with time zone NOT NULL DEFAULT now();

DROP INDEX IF EXISTS auth_db.idx_refresh_tokens_token;
DROP INDEX IF EXISTS auth_db.idx_refresh_tokens_token_unique;
DROP INDEX IF EXISTS auth_db.idx_password_reset_token;
