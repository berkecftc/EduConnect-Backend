-- Create password_reset_tokens table for password reset functionality
CREATE TABLE IF NOT EXISTS auth_db.password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES auth_db.users(id) ON DELETE CASCADE
);

-- Index for faster token lookup
CREATE INDEX IF NOT EXISTS idx_password_reset_token ON auth_db.password_reset_tokens(token);

-- Index for user_id to quickly find/delete tokens by user
CREATE INDEX IF NOT EXISTS idx_password_reset_user_id ON auth_db.password_reset_tokens(user_id);

