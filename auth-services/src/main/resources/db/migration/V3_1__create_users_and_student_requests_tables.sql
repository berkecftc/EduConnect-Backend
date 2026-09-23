CREATE TABLE IF NOT EXISTS auth_db.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_db.user_roles (
    user_id UUID NOT NULL REFERENCES auth_db.users(id) ON DELETE CASCADE,
    role VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE IF NOT EXISTS auth_db.student_requests (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    student_number VARCHAR(255),
    department VARCHAR(255),
    student_document_url VARCHAR(255)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_refresh_token_user') THEN
        ALTER TABLE auth_db.refresh_tokens
        ADD CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES auth_db.users(id) ON DELETE CASCADE;
    END IF;
END $$;
