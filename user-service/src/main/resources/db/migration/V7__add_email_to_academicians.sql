ALTER TABLE user_db.academicians
ADD COLUMN IF NOT EXISTS email VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_academicians_email
    ON user_db.academicians (email)
    WHERE email IS NOT NULL;

COMMENT ON COLUMN user_db.academicians.email IS 'Academician email address from auth-service';

