-- Create archived_academicians table in user_db schema
CREATE TABLE IF NOT EXISTS user_db.archived_academicians (
    archive_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_id UUID NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    department VARCHAR(255),
    office_number VARCHAR(255),
    profile_image_url TEXT,
    deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deletion_reason TEXT
);

-- Create index on original_id for better query performance
CREATE INDEX IF NOT EXISTS idx_archived_academicians_original_id ON user_db.archived_academicians(original_id);

-- Create index on deleted_at for sorting queries
CREATE INDEX IF NOT EXISTS idx_archived_academicians_deleted_at ON user_db.archived_academicians(deleted_at DESC);

-- Create index on department for filtering
CREATE INDEX IF NOT EXISTS idx_archived_academicians_department ON user_db.archived_academicians(department);

