DELETE FROM auth_db.refresh_tokens;

ALTER TABLE auth_db.refresh_tokens ALTER COLUMN token DROP NOT NULL;
ALTER TABLE auth_db.refresh_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);
ALTER TABLE auth_db.refresh_tokens ADD COLUMN IF NOT EXISTS family_id UUID;
ALTER TABLE auth_db.refresh_tokens ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE auth_db.refresh_tokens ALTER COLUMN token_hash SET NOT NULL;
ALTER TABLE auth_db.refresh_tokens ALTER COLUMN family_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_token_hash ON auth_db.refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON auth_db.refresh_tokens(family_id);

DELETE FROM auth_db.password_reset_tokens;

ALTER TABLE auth_db.password_reset_tokens ALTER COLUMN token DROP NOT NULL;
ALTER TABLE auth_db.password_reset_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);
ALTER TABLE auth_db.password_reset_tokens ALTER COLUMN token_hash SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_password_reset_tokens_token_hash ON auth_db.password_reset_tokens(token_hash);

ALTER TABLE auth_db.users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE auth_db.users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;
