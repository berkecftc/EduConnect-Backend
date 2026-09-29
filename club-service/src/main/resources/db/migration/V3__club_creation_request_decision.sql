ALTER TABLE club_db.club_creation_requests ADD COLUMN IF NOT EXISTS rejection_reason text;
ALTER TABLE club_db.club_creation_requests ADD COLUMN IF NOT EXISTS processed_at timestamp(6) without time zone;
ALTER TABLE club_db.club_creation_requests ADD COLUMN IF NOT EXISTS processed_by uuid;

CREATE INDEX IF NOT EXISTS idx_club_creation_requests_advisor_status
    ON club_db.club_creation_requests (suggested_advisor_id, status);
