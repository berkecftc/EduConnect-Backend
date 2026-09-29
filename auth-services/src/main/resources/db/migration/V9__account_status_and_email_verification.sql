ALTER TABLE auth_db.users ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE auth_db.users ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE auth_db.users ADD COLUMN IF NOT EXISTS status_reason TEXT;
ALTER TABLE auth_db.users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE;

UPDATE auth_db.users SET email_verified_at = NOW() WHERE email_verified_at IS NULL;

ALTER TABLE auth_db.users DROP CONSTRAINT IF EXISTS chk_users_status;
ALTER TABLE auth_db.users ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));

ALTER TABLE auth_db.student_requests ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE;

UPDATE auth_db.student_requests SET email_verified_at = NOW() WHERE email_verified_at IS NULL;

CREATE TABLE IF NOT EXISTS auth_db.email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_email_verification_tokens_token_hash ON auth_db.email_verification_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_email ON auth_db.email_verification_tokens(email);
