-- Create archived_clubs table in club_db schema
CREATE TABLE IF NOT EXISTS club_db.archived_clubs (
    archive_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    about TEXT,
    logo_url VARCHAR(500),
    academic_advisor_id UUID,
    deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deletion_reason TEXT,
    deleted_by_admin_id UUID
);

-- Create index on original_id for better query performance
CREATE INDEX IF NOT EXISTS idx_archived_clubs_original_id ON club_db.archived_clubs(original_id);

-- Create index on deleted_at for sorting queries
CREATE INDEX IF NOT EXISTS idx_archived_clubs_deleted_at ON club_db.archived_clubs(deleted_at DESC);

-- Create index on name for search queries
CREATE INDEX IF NOT EXISTS idx_archived_clubs_name ON club_db.archived_clubs(name);

