-- Refresh tokens tablosu oluştur
CREATE TABLE IF NOT EXISTS auth_db.refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index'ler
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON auth_db.refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON auth_db.refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry_date ON auth_db.refresh_tokens(expiry_date);

-- Token kolonuna benzersizlik kontrolü için
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_token_unique ON auth_db.refresh_tokens(token);

-- Foreign key constraint (users tablosu zaten var)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'auth_db' AND table_name = 'users') THEN
        ALTER TABLE auth_db.refresh_tokens
        ADD CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES auth_db.users(id) ON DELETE CASCADE;
    END IF;
END $$;

