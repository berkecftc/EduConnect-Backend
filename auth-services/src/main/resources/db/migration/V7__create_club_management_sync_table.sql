CREATE TABLE IF NOT EXISTS auth_db.club_management_sync (
    user_id       UUID PRIMARY KEY,
    last_event_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_event_id UUID NOT NULL,
    CONSTRAINT fk_club_management_sync_user FOREIGN KEY (user_id) REFERENCES auth_db.users(id) ON DELETE CASCADE
);
